package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.identity.SignInSessions
import telex.identity.StartedSession
import telex.identity.internal.owner.Owners
import telex.messaging.LinkedAccountId
import telex.messaging.MaskedPhone
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.messaging.keyAad
import telex.shared.Uuid7
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.SignInOutcome
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val KEY_BYTES = 32
private const val SESSION_OF = "SELECT telegram_session_id FROM linked_account WHERE id = ?"
private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-linked-accounts-api-it")

/** AC-03, AC-110, AC-113, AC-114: list and unlink my Linked Accounts, Owner-scoped, and the real count in `me`. */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["telex.telegram.adapter=fake"])
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class LinkedAccountsApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var telegram: TelegramSessions

    @Autowired lateinit var ownerKeys: OwnerKeys

    @Autowired lateinit var rows: LinkedAccountRows

    @Autowired lateinit var events: ApplicationEventPublisher

    private val fake get() = telegram as FakeTelegram
    private val http = HttpClient.newHttpClient()
    private val json = JsonMapper()
    private var seq = 0

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner_key")
        jdbc.execute("DELETE FROM owner")
    }

    private fun owner(email: String): OwnerId = owners.findOrCreate(email, email, clock.instant()).first

    private fun start(owner: OwnerId): StartedSession =
        sessions.start(owner, null, "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0", "Europe/Kyiv", false)

    private fun link(
        owner: OwnerId,
        name: String,
        createdAt: Instant,
        chats: Int = 0,
    ): LinkedAccountId {
        val key = ByteArray(KEY_BYTES) { 7 }
        val session = telegram.open(key)
        val phone = "9996600" + "%03d".format(300 + ++seq)
        telegram.sendPhone(session, phone)
        check(telegram.checkCode(session, FakeTelegram.CODE) is SignInOutcome.Authorized)
        val id = LinkedAccountId(Uuid7.next())
        rows.insert(
            NewLinkedAccount(
                id,
                owner,
                phone.toLong(),
                session,
                ownerKeys.seal(owner, key, id.keyAad()),
                name,
                MaskedPhone("999", "%02d".format(seq)),
                createdAt,
            ),
        )
        repeat(chats) {
            jdbc.update(
                "INSERT INTO channel (id, owner_id, linked_account_id, telegram_chat_id, type, title, folder_ids, " +
                    "archived, unread_count, chat_order) VALUES (?, ?, ?, ?, 'private', 'Chat', '{}', false, 0, 0)",
                UUID.randomUUID(),
                owner.value,
                id.value,
                it.toLong(),
            )
        }
        return id
    }

    private fun call(
        method: String,
        path: String,
        key: String?,
        csrf: Boolean = true,
        background: Boolean = false,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies)
        val headers = mutableMapOf<String, String>()
        if (csrf) headers["X-XSRF-TOKEN"] = "csrf"
        if (background) headers["X-Telex-Background"] = "1"
        headers.forEach { (k, v) -> b.header(k, v) }
        b.method(method, HttpRequest.BodyPublishers.noBody())
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(method, path, null, headers, response, ContractValidator.TELEGRAM_LINK_SPEC)
        return response
    }

    private fun names(r: HttpResponse<String>): List<String> {
        val items = json.readTree(r.body())["items"]
        return (0 until items.size()).map { items[it]["displayName"].asString() }
    }

    private fun count() = jdbc.queryForObject("SELECT count(*) FROM linked_account", Int::class.java)

    @Test
    fun `AC-114 the list shows every account oldest first with name, masked phone, state and chat sync`() {
        val o = owner("anton@mail.com")
        val s = start(o)
        val second = link(o, "Work", Instant.parse("2026-10-02T10:00:00Z"), chats = 3)
        val first = link(o, "Personal", Instant.parse("2026-10-01T10:00:00Z"))

        val r = call("GET", "/api/v1/linked-accounts", s.key, background = true)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store")
        assertThat(names(r)).containsExactly("Personal", "Work")
        val items = json.readTree(r.body())["items"]
        assertThat(items[0]["id"].asString()).isEqualTo(first.value.toString())
        assertThat(items[0]["state"].asString()).isEqualTo("connected")
        assertThat(items[1]["id"].asString()).isEqualTo(second.value.toString())
        assertThat(items[1]["phone"]["countryCode"].asString()).isEqualTo("999")
        assertThat(items[1]["chatSync"]["chatsSynced"].asInt()).isEqualTo(3)
    }

    @Test
    fun `AC-114 me counts the callers own accounts`() {
        val a = owner("a@mail.com")
        val b = owner("b@mail.com")
        val s = start(a)
        link(a, "One", Instant.parse("2026-10-01T10:00:00Z"))
        link(a, "Two", Instant.parse("2026-10-02T10:00:00Z"))
        link(b, "Other", Instant.parse("2026-10-02T10:00:00Z"))

        val r =
            http.send(
                HttpRequest
                    .newBuilder(URI.create("http://localhost:$port/api/v1/me"))
                    .header("Cookie", "telex_session=${s.key}")
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertThat(r.body()).contains("\"linkedAccountCount\":2")
    }

    @Test
    fun `AC-03 each Owner sees only their own accounts`() {
        val a = owner("a@mail.com")
        val b = owner("b@mail.com")
        val sa = start(a)
        val sb = start(b)
        link(a, "Mine", Instant.parse("2026-10-01T10:00:00Z"))
        link(b, "Theirs", Instant.parse("2026-10-01T10:00:00Z"))

        assertThat(names(call("GET", "/api/v1/linked-accounts", sa.key))).containsExactly("Mine")
        assertThat(names(call("GET", "/api/v1/linked-accounts", sb.key))).containsExactly("Theirs")
    }

    @Test
    fun `AC-03 unlinking another Owners id and an unknown id answer byte-identical not-found`() {
        val a = owner("a@mail.com")
        val b = owner("b@mail.com")
        val sa = start(a)
        val theirs = link(b, "Theirs", Instant.parse("2026-10-01T10:00:00Z"))

        val foreign = call("DELETE", "/api/v1/linked-accounts/${theirs.value}", sa.key)
        val unknown = call("DELETE", "/api/v1/linked-accounts/${UUID.randomUUID()}", sa.key)

        assertThat(foreign.statusCode()).isEqualTo(404)
        assertThat(foreign.body()).contains("urn:telex:error:not-found")
        assertThat(unknown.statusCode()).isEqualTo(404)
        // identical but for `instance`, which echoes the request path
        val instance = Regex("\"instance\":\"[^\"]*\"")
        assertThat(unknown.body().replace(instance, "")).isEqualTo(foreign.body().replace(instance, ""))
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `unlink without the CSRF header is 403 and keeps the account`() {
        val o = owner("a@mail.com")
        val s = start(o)
        val id = link(o, "Mine", Instant.parse("2026-10-01T10:00:00Z"))

        val r = call("DELETE", "/api/v1/linked-accounts/${id.value}", s.key, csrf = false)

        assertThat(r.statusCode()).isEqualTo(403)
        assertThat(count()).isEqualTo(1)
    }

    @Test
    fun `list needs a signed-in Owner`() {
        assertThat(call("GET", "/api/v1/linked-accounts", null).statusCode()).isEqualTo(401)
    }

    @Test
    fun `AC-111 a confirmed unlink answers signOutConfirmed true and removes the account from the list`() {
        val o = owner("a@mail.com")
        val s = start(o)
        val id = link(o, "Mine", Instant.parse("2026-10-01T10:00:00Z"), chats = 2)

        val r = call("DELETE", "/api/v1/linked-accounts/${id.value}", s.key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"signOutConfirmed\":true")
        assertThat(names(call("GET", "/api/v1/linked-accounts", s.key))).isEmpty()
    }

    @Test
    fun `AC-113 an unreachable Telegram still unlinks and answers signOutConfirmed false`() {
        val o = owner("a@mail.com")
        val s = start(o)
        val id = link(o, "Mine", Instant.parse("2026-10-01T10:00:00Z"))
        val session = jdbc.queryForObject("SELECT telegram_session_id FROM linked_account", UUID::class.java)
        fake.dropConnectivity(TelegramSessionId(checkNotNull(session)))

        val r = call("DELETE", "/api/v1/linked-accounts/${id.value}", s.key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"signOutConfirmed\":false")
        assertThat(names(call("GET", "/api/v1/linked-accounts", s.key))).isEmpty()
    }

    @Test
    fun `AC-110 the accounts are still listed after sign-out and a new sign-in`() {
        val o = owner("a@mail.com")
        val first = start(o)
        val id = link(o, "Mine", Instant.parse("2026-10-01T10:00:00Z"))
        val session = TelegramSessionId(checkNotNull(jdbc.queryForObject(SESSION_OF, UUID::class.java, id.value)))
        sessions.endMine(o, first.sessionId)
        assertThat(call("GET", "/api/v1/linked-accounts", first.key).statusCode()).isEqualTo(401)

        val second = start(o)

        assertThat(names(call("GET", "/api/v1/linked-accounts", second.key))).containsExactly("Mine")
        assertThat(jdbc.queryForObject("SELECT state FROM linked_account", String::class.java)).isEqualTo("connected")
        // The Telegram session outlives the Sign-in Session: still open and authorized, and sync keeps writing.
        assertThat(fake.isOpen(session)).isTrue()
        assertThat(telegram.authorized(session)).isTrue()
        assertThat(fake.wasLoggedOut(session)).isFalse()
        val chat = ChatSnapshot(4_242L, ChatType.Supergroup, "After sign-out", listOf(1), false, 0, 1L)
        events.publishEvent(TelegramChatsChanged(session, listOf(chat), emptyList(), 1, true, null))
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM channel WHERE linked_account_id = ? AND telegram_chat_id = 4242",
                Int::class.java,
                id.value,
            ),
        ).isEqualTo(1)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { SESSIONS_DIR.toString() }
            registry.add("telex.telegram.api-id") { "12345" }
            registry.add("telex.telegram.api-hash") { "abcdef" }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES) { 3 }) }
        }
    }
}
