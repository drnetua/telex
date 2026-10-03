package telex.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.identity.StartedSession
import telex.telegram.internal.fake.FakeTelegram
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-linking-api")
private const val BASE = "/api/v1/linking-attempt"
private const val START = "2026-10-03T10:00:00Z"
private const val PLAIN = "+999 66 0 0005"
private const val HINT_PHONE = "9996610005"
private const val BANNED = "9996630005"
private const val UNREGISTERED = "9996640005"
private const val FLOOD_PHONE = "9996650005"
private const val CODE = "\"code\":\""
private const val INBOX = "{\"origin\":\"inbox\"}"
private const val PASSWORD_HINT = "\"passwordHint\":\"first pet\""

private fun configure(
    registry: DynamicPropertyRegistry,
    credentials: Boolean,
) {
    registry.add("telex.telegram.adapter") { "fake" }
    registry.add("telex.telegram.sessions-dir") { SESSIONS_DIR.toString() }
    registry.add("telex.telegram.api-id") { if (credentials) "12345" else "" }
    registry.add("telex.telegram.api-hash") { if (credentials) "abcdef" else "" }
    registry.add("telex.telegram.max-accounts-per-owner") { "2" }
    registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
}

private class Api(
    private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    fun call(
        method: String,
        path: String,
        key: String?,
        body: String? = null,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies).header("X-XSRF-TOKEN", "csrf")
        if (body != null) b.header("Content-Type", "application/json")
        b.method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        val headers =
            mapOf("X-XSRF-TOKEN" to "csrf") +
                if (body != null) mapOf("Content-Type" to "application/json") else emptyMap()
        ContractValidator.assertConforms(method, path, body, headers, response, ContractValidator.TELEGRAM_LINK_SPEC)
        return response
    }
}

/** AC-02, AC-106, AC-107, AC-109: the linking-attempt endpoints over HTTP on the in-memory Telegram. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class LinkingApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val api = Api(port)
    private val owners = mutableListOf<OwnerId>()
    private val appender = ListAppender<ILoggingEvent>()
    private val root get() = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse(START))
        appender.start()
        root.addAppender(appender)
    }

    @AfterEach
    fun cleanUp() {
        root.detachAppender(appender)
        owners.forEach { jdbc.update("DELETE FROM linked_account WHERE owner_id = ?", it.value) }
    }

    private fun session(owner: OwnerId? = null): Pair<OwnerId, StartedSession> {
        val o =
            owner ?: UUID.randomUUID().let {
                jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", it, "$it@mail.com", "$it@mail.com")
                OwnerId(it).also(owners::add)
            }
        return o to sessions.start(o, null, null, null, false)
    }

    private fun phone(
        key: String,
        number: String = PLAIN,
    ) = api.call("POST", "$BASE/phone", key, "{\"phoneNumber\":\"$number\"}")

    private fun code(
        key: String,
        value: String = FakeTelegram.CODE,
    ) = api.call("POST", "$BASE/code", key, "{\"code\":\"$value\"}")

    private fun password(
        key: String,
        value: String,
    ) = api.call("POST", "$BASE/password", key, "{\"password\":\"$value\"}")

    @Test
    fun `AC-109 start opens an attempt at the phone step and a second session of the owner resumes it`() {
        val (owner, first) = session()
        val (_, second) = session(owner)

        val started = api.call("POST", BASE, first.key, INBOX)
        assertThat(started.statusCode()).isEqualTo(201)
        assertThat(started.body()).contains("\"step\":\"phone\"", "\"origin\":\"inbox\"", "\"codeLength\":null")
        phone(first.key)

        val resumed = api.call("POST", BASE, second.key, "{\"origin\":\"accounts\"}")
        assertThat(resumed.statusCode()).isEqualTo(200)
        assertThat(resumed.body()).contains("\"step\":\"code\"", "\"origin\":\"inbox\"", "\"codeLength\":5")
        assertThat(api.call("GET", BASE, second.key).statusCode()).isEqualTo(200)
    }

    @Test
    fun `AC-109 cancel is 204 even twice, and the attempt is then not found`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)

        assertThat(api.call("DELETE", BASE, s.key).statusCode()).isEqualTo(204)
        assertThat(api.call("DELETE", BASE, s.key).statusCode()).isEqualTo(204)
        val gone = api.call("GET", BASE, s.key)
        assertThat(gone.statusCode()).isEqualTo(404)
        assertThat(gone.body()).contains("${CODE}linking-attempt-not-found\"")
    }

    @Test
    fun `AC-109 an attempt idle for 15 minutes is not found`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)
        clock.advance(Duration.ofMinutes(16))

        assertThat(api.call("GET", BASE, s.key).statusCode()).isEqualTo(404)
    }

    @Test
    fun `AC-107 a phone Telegram refuses is 422 with its reason and the attempt stays at the phone step`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)

        listOf("+ ()" to "invalid", "12345" to "invalid", UNREGISTERED to "unregistered", BANNED to "banned")
            .forEach { (number, reason) ->
                val r = phone(s.key, number)
                assertThat(r.statusCode()).isEqualTo(422)
                assertThat(r.body()).contains("${CODE}telegram-phone-$reason\"")
            }
        assertThat(api.call("GET", BASE, s.key).body()).contains("\"step\":\"phone\"")
    }

    @Test
    fun `AC-02 a wrong or expired code is 422 and the attempt stays, a new code can be asked`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)
        assertThat(phone(s.key).statusCode()).isEqualTo(200)

        assertThat(code(s.key, "11111").body()).contains("${CODE}telegram-code-wrong\"")
        val expired = code(s.key, FakeTelegram.EXPIRED_CODE)
        assertThat(expired.statusCode()).isEqualTo(422)
        assertThat(expired.body()).contains("${CODE}telegram-code-expired\"")
        val resent = api.call("POST", "$BASE/code/resend", s.key)
        assertThat(resent.statusCode()).isEqualTo(200)
        assertThat(resent.body()).contains("\"step\":\"code\"")
    }

    @Test
    fun `AC-02 a Telegram wait is 429 with retryAt and Retry-After and ends the attempt`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)

        val r = phone(s.key, FLOOD_PHONE)

        assertThat(r.statusCode()).isEqualTo(429)
        assertThat(r.headers().firstValue("Retry-After")).hasValue(FakeTelegram.FLOOD_WAIT_SECONDS.toString())
        assertThat(r.body()).contains("${CODE}telegram-wait-required\"", "\"retryAt\":\"2026-10-03T10:00:30Z\"")
        assertThat(api.call("GET", BASE, s.key).statusCode()).isEqualTo(404)
    }

    @Test
    fun `AC-106 a wrong password is 422 with the hint, the right one links the account`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)
        phone(s.key, HINT_PHONE)

        val next = code(s.key)
        assertThat(next.statusCode()).isEqualTo(200)
        assertThat(next.body()).contains("\"outcome\":\"next\"", "\"step\":\"password\"", PASSWORD_HINT)
        val wrong = password(s.key, "nope")
        assertThat(wrong.statusCode()).isEqualTo(422)
        assertThat(wrong.body()).contains("${CODE}telegram-password-wrong\"", PASSWORD_HINT)

        val linked = password(s.key, FakeTelegram.PASSWORD)
        assertThat(linked.statusCode()).isEqualTo(200)
        assertThat(linked.body()).contains("\"outcome\":\"linked\"", "\"linkedAccountId\":\"", "\"origin\":\"inbox\"")
        assertThat(api.call("GET", BASE, s.key).statusCode()).isEqualTo(404)
    }

    @Test
    fun `a step at the wrong place is 409 linking-step-mismatch naming the current step`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)

        val r = code(s.key)

        assertThat(r.statusCode()).isEqualTo(409)
        assertThat(r.body()).contains("${CODE}linking-step-mismatch\"", "\"step\":\"phone\"")
        assertThat(api.call("POST", "$BASE/code/resend", s.key).statusCode()).isEqualTo(409)
    }

    @Test
    fun `steps without an open attempt are 404 linking-attempt-not-found`() {
        val (_, s) = session()

        assertThat(phone(s.key).body()).contains("${CODE}linking-attempt-not-found\"")
        assertThat(code(s.key).statusCode()).isEqualTo(404)
        assertThat(password(s.key, "x").statusCode()).isEqualTo(404)
        assertThat(api.call("POST", "$BASE/code/resend", s.key).statusCode()).isEqualTo(404)
    }

    @Test
    fun `invalid bodies are 400 validation-failed naming the field`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)
        phone(s.key)

        val badCode = code(s.key, "12a45")
        assertThat(badCode.statusCode()).isEqualTo(400)
        assertThat(badCode.body()).contains("${CODE}validation-failed\"", "\"field\":\"code\"")
        val blank = api.call("POST", "$BASE/phone", s.key, "{\"phoneNumber\":\"\"}")
        assertThat(blank.statusCode()).isEqualTo(400)
        assertThat(blank.body()).contains("\"field\":\"phoneNumber\"")
        assertThat(password(s.key, "").statusCode()).isEqualTo(400)
        assertThat(api.call("POST", BASE, s.key, "{}").statusCode()).isEqualTo(400)
        assertThat(api.call("POST", BASE, s.key, "{\"origin\":\"nowhere\"}").statusCode()).isEqualTo(400)
    }

    @Test
    fun `signing in again for an account that is not the owner's is 404 not-found`() {
        val (_, s) = session()
        val body = "{\"origin\":\"accounts\",\"targetLinkedAccountId\":\"${UUID.randomUUID()}\"}"

        val r = api.call("POST", BASE, s.key, body)

        assertThat(r.statusCode()).isEqualTo(404)
        assertThat(r.body()).contains("${CODE}not-found\"")
    }

    @Test
    fun `without a session every operation is 401`() {
        assertThat(api.call("GET", BASE, null).statusCode()).isEqualTo(401)
        assertThat(api.call("POST", BASE, null, INBOX).statusCode()).isEqualTo(401)
        assertThat(api.call("DELETE", BASE, null).statusCode()).isEqualTo(401)
    }

    @Test
    fun `no phone, code or password reaches the log`() {
        val (_, s) = session()
        api.call("POST", BASE, s.key, INBOX)
        phone(s.key, HINT_PHONE)
        code(s.key, "11111")
        code(s.key)
        password(s.key, "hunter2-secret")

        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        assertThat(logged).doesNotContain(HINT_PHONE, "hunter2-secret", "11111")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = configure(registry, credentials = true)
    }
}

/** AC-119: without Telegram app credentials the start answers "not set up" and opens nothing. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class LinkingApiNotSetUpIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `AC-119 starting without credentials is 503 telegram-linking-not-set-up`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val s = sessions.start(OwnerId(id), null, null, null, false)
        val api = Api(port)

        val r = api.call("POST", BASE, s.key, INBOX)

        assertThat(r.statusCode()).isEqualTo(503)
        assertThat(r.body()).contains("${CODE}telegram-linking-not-set-up\"")
        assertThat(api.call("GET", BASE, s.key).statusCode()).isEqualTo(404)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = configure(registry, credentials = false)
    }
}
