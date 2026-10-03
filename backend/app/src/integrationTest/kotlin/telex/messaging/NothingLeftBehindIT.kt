package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.lifecycle.BootReconnect
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID
import kotlin.io.path.listDirectoryEntries

private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-nothing-left")
private const val CHATS = 50
private const val PHONE_CONFIRMED = "9996600050"
private const val PHONE_UNREACHABLE = "9996600051"
private const val PHONE_OTHER = "9996600052"
private const val PHONE_CRASH = "9996600053"

/** AC-03, AC-112 (QG-1b): after an unlink, confirmed or unreachable, nothing of the account is left in teleX. */
@SpringBootTest(properties = ["telex.telegram.adapter=fake"])
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class NothingLeftBehindIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var telegram: TelegramSessions

    @Autowired lateinit var deletion: AccountDeletion

    @Autowired lateinit var boot: BootReconnect

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEventPublisher

    private val fake get() = telegram as FakeTelegram

    private class Linked(
        val owner: OwnerId,
        val id: LinkedAccountId,
        val session: TelegramSessionId,
    )

    @BeforeEach
    fun clean() {
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
    }

    private fun linkAndSync(phone: String): Linked {
        val uuid = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", uuid, "$uuid@mail.com", "$uuid@mail.com")
        val owner = OwnerId(uuid)
        val signIn = signInSessions.start(owner, null, null, null, false).sessionId
        linking.start(owner, signIn, LinkingOrigin.INBOX, null)
        linking.submitPhone(owner, signIn, phone)
        val done = linking.submitCode(owner, signIn, FakeTelegram.CODE) as LinkingProgress.Completed
        val session =
            jdbc.queryForObject(
                "SELECT telegram_session_id FROM linked_account WHERE id = ?",
                UUID::class.java,
                done.linkedAccountId.value,
            )!!
        syncChats(TelegramSessionId(session), phone.takeLast(4).toInt())
        await().atMost(Duration.ofSeconds(10)).until { chatsOf(done.linkedAccountId) == phone.takeLast(4).toInt() }
        return Linked(owner, done.linkedAccountId, TelegramSessionId(session))
    }

    private fun syncChats(
        session: TelegramSessionId,
        total: Int,
    ) {
        val chats =
            List(total) {
                ChatSnapshot(
                    -1_000_000_000_000L - it,
                    ChatType.Supergroup,
                    "Test chat ${it + 1}",
                    emptyList(),
                    false,
                    0,
                    (
                        total -
                            it
                    ).toLong(),
                )
            }
        events.publishEvent(TelegramChatsChanged(session, chats, emptyList(), total, true))
    }

    private fun chatsOf(id: LinkedAccountId) =
        jdbc.queryForObject("SELECT count(*) FROM channel WHERE linked_account_id = ?", Int::class.java, id.value)!!

    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    private fun dir(session: TelegramSessionId) = SESSIONS_DIR.resolve(session.value.toString())

    /** Every row of every table, as text: the stand-in for a database dump. */
    private fun dumpHits(
        needle: String,
        skip: Set<String> = emptySet(),
    ): List<String> =
        jdbc
            .queryForList(
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String::class.java,
            ).filterNotNull()
            .filter { it !in skip }
            .filter {
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"$it\" t WHERE t::text LIKE ?",
                    Int::class.java,
                    "%$needle%",
                )!! >
                    0
            }

    private fun assertNothingLeft(
        linked: Linked,
        phone: String,
    ) {
        assertThat(count("linked_account")).isZero()
        assertThat(count("channel")).isZero()
        assertThat(fake.isOpen(linked.session)).isFalse()
        assertThat(Files.exists(dir(linked.session))).isFalse()
        assertThat(SESSIONS_DIR.listDirectoryEntries().map { it.fileName.toString() })
            .doesNotContain(linked.session.value.toString())
        assertThat(accounts.listMine(linked.owner)).isEmpty()
        for (needle in listOf(phone, "Test user ${phone.takeLast(4)}", "Test chat", linked.session.value.toString())) {
            assertThat(dumpHits(needle)).describedAs("tables holding '%s'", needle).isEmpty()
        }
        // the account id survives only as an opaque id inside the event registry
        val skip = setOf("event_publication", "event_publication_archive")
        assertThat(dumpHits(linked.id.value.toString(), skip)).isEmpty()
    }

    @Test
    fun `AC-112 a confirmed unlink of an account with 50 synced chats leaves no row, key, directory or session`() {
        val linked = linkAndSync(PHONE_CONFIRMED)
        assertThat(count("channel")).isEqualTo(CHATS)

        assertThat(accounts.unlink(linked.owner, linked.id).signOutConfirmed).isTrue()

        assertThat(fake.wasLoggedOut(linked.session)).isTrue()
        assertNothingLeft(linked, PHONE_CONFIRMED)
    }

    @Test
    fun `AC-112 an unlink while Telegram is unreachable still leaves nothing in teleX`() {
        val linked = linkAndSync(PHONE_UNREACHABLE)
        fake.dropConnectivity(linked.session)

        assertThat(accounts.unlink(linked.owner, linked.id).signOutConfirmed).isFalse()

        assertThat(fake.wasLoggedOut(linked.session)).isFalse()
        assertNothingLeft(linked, PHONE_UNREACHABLE)
    }

    @Test
    fun `AC-112 a stop after the commit and before the directory is destroyed is cleaned by the sweep on restart`() {
        val linked = linkAndSync(PHONE_CRASH)
        val keep = linkAndSync(PHONE_OTHER)

        // the unlink transaction commits, then the process dies before close and destroy
        assertThat(deletion.delete(linked.owner, linked.id)).isTrue()
        fake.simulateStop()
        assertThat(Files.exists(dir(linked.session))).isTrue()

        boot.run().join()

        assertThat(Files.exists(dir(linked.session))).isFalse()
        assertThat(Files.exists(dir(keep.session))).isTrue()
        assertThat(count("linked_account")).isEqualTo(1)
        assertThat(accounts.listMine(linked.owner)).isEmpty()
    }

    @Test
    fun `AC-03 another Owner sees, signs in again to and unlinks nothing of an account, and no chat of it`() {
        val mine = linkAndSync(PHONE_CONFIRMED)
        val theirs = linkAndSync(PHONE_OTHER)

        assertThat(accounts.listMine(mine.owner).map { it.id }).containsExactly(mine.id)
        assertThat(accounts.listMine(theirs.owner).map { it.id }).containsExactly(theirs.id)
        assertThatThrownBy { accounts.unlink(theirs.owner, mine.id) }.isInstanceOf(LinkedAccountNotFound::class.java)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM channel WHERE linked_account_id = ? AND owner_id = ?",
                Int::class.java,
                mine.id.value,
                theirs.owner.value,
            ),
        ).isZero()
        assertThat(count("linked_account")).isEqualTo(2)
        assertThat(fake.wasLoggedOut(mine.session)).isFalse()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { SESSIONS_DIR.toString() }
            registry.add("telex.telegram.api-id") { "54321" }
            registry.add("telex.telegram.api-hash") { "abcdef" }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 9 }) }
        }
    }
}
