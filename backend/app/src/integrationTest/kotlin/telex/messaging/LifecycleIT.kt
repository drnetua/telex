package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.util.AopTestUtils
import org.springframework.test.util.ReflectionTestUtils
import telex.TestcontainersConfiguration
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.messaging.internal.lifecycle.BootReconnect
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** AC-36, AC-118, AC-117, AC-122, AC-111: accounts come back after a restart and follow Telegram's session state. */
@SpringBootTest(properties = ["telex.telegram.adapter=fake"])
@Import(TestcontainersConfiguration::class)
@RecordApplicationEvents
class LifecycleIT {
    @Autowired lateinit var boot: BootReconnect

    @Autowired lateinit var telegram: TelegramSessions

    @Autowired lateinit var ownerKeys: OwnerKeys

    @MockitoSpyBean lateinit var rows: LinkedAccountRows

    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

    private val fake get() = telegram as FakeTelegram
    private var phoneSeq = 0

    @BeforeEach
    fun clean() {
        Mockito.reset(AopTestUtils.getUltimateTargetObject<LinkedAccountRows>(rows))
        fake.reopenUnavailable = false
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
    }

    private class Linked(
        val id: LinkedAccountId,
        val owner: OwnerId,
        val session: TelegramSessionId,
    )

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    /** A connected account on an authorized fake session, its TDLib key sealed with the Owner's key. */
    private fun connected(owner: OwnerId): Linked {
        val key = ByteArray(KEY_BYTES) { 7 }
        val session = telegram.open(key)
        val phone = "9996600" + "%03d".format(++phoneSeq + 100)
        telegram.sendPhone(session, phone)
        check(telegram.checkCode(session, FakeTelegram.CODE) is SignInOutcome.Authorized)
        val id = LinkedAccountId(Uuid7.next())
        val sealed = ownerKeys.seal(owner, key, id.keyAad())
        rows.insert(
            NewLinkedAccount(
                id,
                owner,
                phone.toLong(),
                session,
                sealed,
                "Anna",
                MaskedPhone("999", "00"),
                Instant.now(),
            ),
        )
        return Linked(id, owner, session)
    }

    private fun state(account: Linked) =
        jdbc.queryForObject("SELECT state FROM linked_account WHERE id = ?", String::class.java, account.id.value)

    private fun restart() {
        fake.simulateStop()
        boot.run().get(RESTART_SECONDS, TimeUnit.SECONDS)
    }

    private fun changes() = events.stream(LinkedAccountStateChanged::class.java).toList()

    @Test
    fun `AC-36 both accounts are connected again after a restart without any code`() {
        val owner = owner()
        val a = connected(owner)
        val b = connected(owner)
        jdbc.update("UPDATE linked_account SET state = 'reconnecting'")

        restart()

        assertThat(state(a)).isEqualTo("connected")
        assertThat(state(b)).isEqualTo("connected")
        assertThat(fake.isOpen(a.session)).isTrue()
        assertThat(fake.isOpen(b.session)).isTrue()
    }

    @Test
    fun `AC-118 the account Telegram ended while stopped is Session lost and the other is connected`() {
        val owner = owner()
        val ended = connected(owner)
        val alive = connected(owner)
        fake.endWhileStopped(ended.session)

        restart()

        assertThat(state(ended)).isEqualTo("session_lost")
        assertThat(state(alive)).isEqualTo("connected")
        assertThat(changes().map { it.linkedAccountId }).containsExactly(ended.id)
        assertThat(fake.isOpen(ended.session)).isFalse()
    }

    @Test
    fun `AC-117 a session Telegram terminates becomes Session lost and keeps its directory`() {
        val account = connected(owner())

        fake.terminate(account.session)

        assertThat(state(account)).isEqualTo("session_lost")
        assertThat(
            jdbc.queryForObject(
                "SELECT telegram_session_id FROM linked_account WHERE id = ?",
                UUID::class.java,
                account.id.value,
            ),
        ).isEqualTo(account.session.value)
        assertThat(Files.exists(sessionsRoot.resolve(account.session.value.toString()))).isTrue()
        assertThat(changes().single().state).isEqualTo(LinkedAccountState.SESSION_LOST)
    }

    @Test
    fun `AC-122 an outage is Reconnecting and never Session lost, then Connected again`() {
        val account = connected(owner())

        fake.dropConnectivity(account.session)
        assertThat(state(account)).isEqualTo("reconnecting")

        fake.restoreConnectivity(account.session)
        assertThat(state(account)).isEqualTo("connected")
        assertThat(changes().map { it.state })
            .containsExactly(LinkedAccountState.RECONNECTING, LinkedAccountState.CONNECTED)
    }

    @Test
    fun `a repeated state publishes nothing and an event of an unknown session is dropped`() {
        val account = connected(owner())

        fake.restoreConnectivity(account.session)
        fake.restoreConnectivity(account.session)
        val stranger = telegram.open(ByteArray(KEY_BYTES))
        telegram.sendPhone(stranger, "9996600999")
        telegram.checkCode(stranger, FakeTelegram.CODE)
        fake.terminate(stranger)

        assertThat(changes()).isEmpty()
        assertThat(state(account)).isEqualTo("connected")
    }

    /**
     * Runs [race] once, right after the boot reopen reads [account]'s sealed key. Later reads (the unlink's own
     * reopen inside [race]) go straight to the real method, so the stub never recurses.
     */
    private fun afterBootReadsKey(
        account: Linked,
        race: () -> Unit,
    ) {
        val fired = AtomicBoolean()
        Mockito
            .doAnswer { call -> call.callRealMethod().also { if (fired.compareAndSet(false, true)) race() } }
            .`when`(rows)
            .sealedKey(account.id)
    }

    @Test
    fun `AC-111 an account unlinked while boot reopens it keeps no open session and no directory`() {
        val account = connected(owner())
        // The unlink lands after the reopen read the key but before the reopened session is registered.
        afterBootReadsKey(account) { accounts.unlink(account.owner, account.id) }

        restart()

        assertThat(jdbc.queryForObject("SELECT count(*) FROM linked_account", Int::class.java)).isZero()
        assertThat(fake.isOpen(account.session)).isFalse()
        assertThat(Files.exists(sessionsRoot.resolve(account.session.value.toString()))).isFalse()
    }

    @Test
    fun `AC-111 an account unlinked while boot reopens it is signed out of Telegram and reports it confirmed`() {
        val account = connected(owner())
        val result =
            java.util.concurrent.atomic
                .AtomicReference<telex.messaging.UnlinkResult>()
        afterBootReadsKey(account) { result.set(accounts.unlink(account.owner, account.id)) }

        restart()

        assertThat(fake.wasLoggedOut(account.session)).`as`("teleX device signed out of Telegram").isTrue()
        assertThat(result.get().signOutConfirmed).isTrue()
        assertThat(fake.isOpen(account.session)).isFalse()
    }

    @Test
    fun `AC-111 a reopen after an unlink could not sign out leaves it unconfirmed, with no session or directory`() {
        val account = connected(owner())
        // The unlink cannot reach Telegram (its own reopen fails); the boot reopen it raced finds an empty database.
        val result =
            java.util.concurrent.atomic
                .AtomicReference<telex.messaging.UnlinkResult>()
        afterBootReadsKey(account) {
            fake.reopenUnavailable = true
            try {
                result.set(accounts.unlink(account.owner, account.id))
            } finally {
                fake.reopenUnavailable = false
            }
        }

        restart()

        assertThat(result.get().signOutConfirmed).isFalse()
        assertThat(fake.wasLoggedOut(account.session)).`as`("an empty database cannot be signed out").isFalse()
        assertThat(fake.isOpen(account.session)).isFalse()
        assertThat(Files.exists(sessionsRoot.resolve(account.session.value.toString()))).isFalse()
    }

    @Test
    fun `AC-113 a reopen that fails after an unlink leaves no session directory`() {
        val account = connected(owner())
        afterBootReadsKey(account) {
            fake.reopenUnavailable = true
            accounts.unlink(account.owner, account.id)
        }

        restart()

        assertThat(jdbc.queryForObject("SELECT count(*) FROM linked_account", Int::class.java)).isZero()
        assertThat(Files.exists(sessionsRoot.resolve(account.session.value.toString()))).isFalse()
    }

    @Test
    fun `a master-key reset puts every account in Session lost and clears the session columns`() {
        val owner = owner()
        val a = connected(owner)
        connected(owner)
        ReflectionTestUtils.setField(ownerKeys, "resetPerformedAtStartup", true)
        try {
            restart()
        } finally {
            ReflectionTestUtils.setField(ownerKeys, "resetPerformedAtStartup", false)
        }

        assertThat(jdbc.queryForList("SELECT state FROM linked_account").map { it["state"] })
            .containsOnly("session_lost")
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM linked_account WHERE telegram_session_id IS NOT NULL",
                Int::class.java,
            ),
        ).isZero()
        assertThat(changes()).hasSize(2)
        assertThat(Files.exists(sessionsRoot.resolve(a.session.value.toString()))).isFalse()
    }

    companion object {
        private const val KEY_BYTES = 32
        private const val RESTART_SECONDS = 60L
        private val sessionsRoot: Path = Files.createTempDirectory("telex-lifecycle-it")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { sessionsRoot.toString() }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES) { 3 }) }
        }
    }
}
