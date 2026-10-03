package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.identity.SignInSessions
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.messaging.internal.attempt.LinkingAttempts
import telex.shared.Uuid7
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val KEY_BYTES = 32
private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-unlink-it")

/** AC-111, AC-112, AC-113: unlink signs out within a bound, deletes everything in one transaction, then destroys. */
@SpringBootTest(properties = ["telex.telegram.adapter=fake"])
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
@RecordApplicationEvents
class UnlinkIT {
    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var telegram: TelegramSessions

    @Autowired lateinit var ownerKeys: OwnerKeys

    @Autowired lateinit var rows: LinkedAccountRows

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

    @Autowired lateinit var meters: MeterRegistry

    @Autowired lateinit var linking: Linking

    @Autowired lateinit var deletion: AccountDeletion

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var attempts: LinkingAttempts

    private val fake get() = telegram as FakeTelegram
    private var phoneSeq = 0

    private class Linked(
        val id: LinkedAccountId,
        val owner: OwnerId,
        val session: TelegramSessionId?,
    )

    @BeforeEach
    fun clean() {
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
    }

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun connected(
        owner: OwnerId,
        chats: Int = 2,
    ): Linked {
        val key = ByteArray(KEY_BYTES) { 7 }
        val session = telegram.open(key)
        val phone = "9996600" + "%03d".format(++phoneSeq + 200)
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
                "Anna",
                MaskedPhone("99", "00"),
                Instant.now(),
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
        return Linked(id, owner, session)
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)

    private fun sessionDir(session: TelegramSessionId) = SESSIONS_DIR.resolve(session.value.toString())

    private fun unlinkedCounter(signout: String) = meters.counter("telex.unlink", "signout", signout).count()

    @Test
    fun `AC-111 a confirmed unlink signs out, deletes the account and its chats and destroys the session`() {
        val owner = owner()
        val account = connected(owner)
        val session = account.session!!
        val before = unlinkedCounter("confirmed")

        val result = accounts.unlink(owner, account.id)

        assertThat(result.signOutConfirmed).isTrue()
        assertThat(fake.wasLoggedOut(session)).isTrue()
        assertThat(count("linked_account")).isZero()
        assertThat(count("channel")).isZero()
        assertThat(fake.isOpen(session)).isFalse()
        assertThat(Files.exists(sessionDir(session))).isFalse()
        assertThat(accounts.listMine(owner)).isEmpty()
        assertThat(unlinkedCounter("confirmed")).isEqualTo(before + 1)
        assertThat(events.stream(AccountUnlinked::class.java).toList()).contains(AccountUnlinked(owner, account.id))
    }

    @Test
    fun `AC-112 AccountUnlinked is recorded in the event registry and carries ids only`() {
        val owner = owner()
        val account = connected(owner)
        jdbc.execute("DELETE FROM event_publication")

        accounts.unlink(owner, account.id)

        val serialized =
            jdbc.queryForList(
                "SELECT serialized_event FROM event_publication WHERE event_type LIKE '%AccountUnlinked'",
                String::class.java,
            )
        assertThat(serialized).hasSize(1)
        assertThat(serialized.single()).contains(account.id.value.toString()).doesNotContain("Anna", "9996600")
    }

    @Test
    fun `AC-112 linking the same Telegram account again after an unlink is a new account with nothing attached`() {
        val owner = owner()
        val account = connected(owner)
        accounts.unlink(owner, account.id)

        val again = connected(owner, chats = 0)

        assertThat(again.id).isNotEqualTo(account.id)
        assertThat(count("channel")).isZero()
        assertThat(accounts.listMine(owner)).hasSize(1)
    }

    @Test
    fun `AC-113 an unreachable Telegram does not stop the unlink and the sign-out is not confirmed`() {
        val owner = owner()
        val account = connected(owner)
        val session = account.session!!
        fake.dropConnectivity(session)
        val before = unlinkedCounter("unconfirmed")

        val result = accounts.unlink(owner, account.id)

        assertThat(result.signOutConfirmed).isFalse()
        assertThat(fake.wasLoggedOut(session)).isFalse()
        assertThat(count("linked_account")).isZero()
        assertThat(count("channel")).isZero()
        assertThat(Files.exists(sessionDir(session))).isFalse()
        assertThat(unlinkedCounter("unconfirmed")).isEqualTo(before + 1)
    }

    @Test
    fun `AC-113 a Session lost account is deleted without a sign-out attempt`() {
        val owner = owner()
        val account = connected(owner)
        val session = account.session!!
        fake.terminate(session)
        await().untilAsserted {
            assertThat(
                jdbc.queryForObject(
                    "SELECT state FROM linked_account WHERE id = ?",
                    String::class.java,
                    account.id.value,
                ),
            ).isEqualTo("session_lost")
        }
        val before = unlinkedCounter("unconfirmed")

        val result = accounts.unlink(owner, account.id)

        assertThat(result.signOutConfirmed).isFalse()
        assertThat(fake.wasLoggedOut(session)).isFalse()
        assertThat(count("linked_account")).isZero()
        assertThat(count("channel")).isZero()
        assertThat(Files.exists(sessionDir(session))).isFalse()
        assertThat(unlinkedCounter("unconfirmed")).isEqualTo(before + 1)
    }

    @Test
    fun `AC-111 the delete reports the session it removed, so a session swapped in meanwhile is not left behind`() {
        val owner = owner()
        val account = connected(owner)
        val stale = account.session!!
        jdbc.update("UPDATE linked_account SET state = 'session_lost' WHERE id = ?", account.id.value)
        val replacement = telegram.open(ByteArray(KEY_BYTES) { 7 })
        rows.swapSession(
            account.id,
            replacement,
            ownerKeys.seal(
                owner,
                ByteArray(KEY_BYTES) {
                    7
                },
                account.id.keyAad(),
            ),
            "Anna",
        )

        val removed = deletion.delete(owner, account.id)

        assertThat(removed?.session).isEqualTo(replacement).isNotEqualTo(stale)
    }

    @Test
    fun `AC-03 another Owner's account or one already unlinked is not found and nothing changes`() {
        val owner = owner()
        val account = connected(owner)
        val stranger = owner()

        assertThatThrownBy { accounts.unlink(stranger, account.id) }.isInstanceOf(LinkedAccountNotFound::class.java)
        assertThat(count("linked_account")).isEqualTo(1)
        assertThat(count("channel")).isEqualTo(2)
        assertThat(fake.wasLoggedOut(account.session!!)).isFalse()

        accounts.unlink(owner, account.id)
        assertThatThrownBy { accounts.unlink(owner, account.id) }.isInstanceOf(LinkedAccountNotFound::class.java)
    }

    @Test
    fun `AC-111 an open sign-in-again attempt targeting the account is discarded`() {
        val owner = owner()
        val account = connected(owner)
        jdbc.update("UPDATE linked_account SET state = 'session_lost' WHERE id = ?", account.id.value)
        val signIn = signInSessions.start(owner, null, null, null, false).sessionId
        linking.start(owner, signIn, LinkingOrigin.ACCOUNTS, account.id)
        assertThat(attempts.find(owner)).isNotNull

        accounts.unlink(owner, account.id)

        assertThat(attempts.find(owner)).isNull()
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
