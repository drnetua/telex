package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
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
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.messaging.internal.account.LinkCompletion
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.attempt.LinkingAttempts
import telex.shared.DomainProblem
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUser
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries

private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-linking-completion")
private const val CODE = "code"
private const val SEALED_BYTES = 60
private const val STATE = "state"
private const val SESSION_ID = "telegram_session_id"

/** AC-01, AC-04, AC-108, AC-115, AC-117: an authorized attempt links, signs in again, or is refused and logged out. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
@RecordApplicationEvents
@Suppress("TooManyFunctions") // one scenario per test
class LinkingCompletionIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var attempts: LinkingAttempts

    @Autowired lateinit var telegram: TelegramSessions

    @MockitoSpyBean lateinit var rows: LinkedAccountRows

    @MockitoSpyBean lateinit var completion: LinkCompletion

    @Autowired lateinit var ownerKeys: OwnerKeys

    @Autowired lateinit var meters: MeterRegistry

    @Autowired lateinit var events: ApplicationEvents

    private var owner = OwnerId(UUID.randomUUID())
    private var session = SignInSessionId(UUID.randomUUID())

    private val fake get() = telegram as FakeTelegram

    private fun newOwner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    @BeforeEach
    fun reset() {
        owner = newOwner()
        session = signInSessions.start(owner, null, null, null, false).sessionId
    }

    @AfterEach
    fun cleanUp() {
        Mockito.reset(
            AopTestUtils.getUltimateTargetObject<LinkedAccountRows>(rows),
            AopTestUtils.getUltimateTargetObject<LinkCompletion>(completion),
        )
        fake.failAfterAuthorization = false
        linking.cancel(owner)
    }

    private fun refusal(block: () -> Any?): Map<String, Any?> {
        val thrown =
            try {
                block()
                null
            } catch (e: DomainProblem) {
                e
            }
        assertThat(thrown).isNotNull
        return thrown!!.body.properties.orEmpty() + ("status" to thrown.statusCode.value())
    }

    private fun toCodeStep(
        phone: String,
        target: LinkedAccountId? = null,
        origin: LinkingOrigin = LinkingOrigin.INBOX,
    ) {
        linking.start(owner, session, origin, target)
        linking.submitPhone(owner, session, phone)
    }

    private fun finish() = linking.submitCode(owner, session, FakeTelegram.CODE)

    private fun insertAccount(
        forOwner: OwnerId,
        telegramUserId: Long,
        state: LinkedAccountState = LinkedAccountState.CONNECTED,
        sessionId: TelegramSessionId? = null,
    ): LinkedAccountId {
        val id = LinkedAccountId(Uuid7.next())
        val held =
            if (state ==
                LinkedAccountState.SESSION_LOST
            ) {
                null
            } else {
                sessionId ?: TelegramSessionId(UUID.randomUUID())
            }
        jdbc.update(
            "INSERT INTO linked_account (id, owner_id, telegram_user_id, telegram_session_id, tdlib_key_sealed, " +
                "display_name, phone_country_code, phone_last_digits, state, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 'Old name', '11', '11', ?, now())",
            id.value,
            forOwner.value,
            telegramUserId,
            held?.value,
            held?.let { ByteArray(SEALED_BYTES) { 1 } },
            state.wire,
        )
        return id
    }

    private fun sessionDirectories() = SESSIONS_DIR.listDirectoryEntries().size

    private fun rowsOf(forOwner: OwnerId) =
        jdbc.queryForList("SELECT * FROM linked_account WHERE owner_id = ?", forOwner.value)

    private fun attemptSession() = attempts.find(owner)!!.sessionId

    @Test
    fun `AC-01 an account with two-step verification becomes a connected Linked Account with a masked phone`() {
        toCodeStep("9996610101")
        val toPassword = linking.submitCode(owner, session, FakeTelegram.CODE) as LinkingProgress.Step
        assertThat(toPassword.attempt.step).isEqualTo(LinkingStep.PASSWORD)
        val sessionId = attemptSession()

        val done = linking.submitPassword(owner, session, FakeTelegram.PASSWORD) as LinkingProgress.Completed

        assertThat(done.outcome).isEqualTo(LinkingOutcome.LINKED)
        assertThat(done.origin).isEqualTo(LinkingOrigin.INBOX)
        val row = rowsOf(owner).single()
        assertThat(row["id"]).isEqualTo(done.linkedAccountId.value)
        assertThat(row[STATE]).isEqualTo("connected")
        assertThat(row["display_name"]).isEqualTo("Test user 0101")
        assertThat(row["phone_country_code"]).isEqualTo("99")
        assertThat((row["phone_last_digits"] as String).trim()).isEqualTo("01")
        assertThat(row["telegram_user_id"]).isEqualTo(9996610101L)
        assertThat(row[SESSION_ID]).isEqualTo(sessionId.value)
        val sealed = row["tdlib_key_sealed"] as ByteArray
        assertThat(sealed).hasSize(SEALED_BYTES)
        assertThat(ownerKeys.open(owner, sealed, done.linkedAccountId.keyAad())).hasSize(32)
        assertThat(events.stream(AccountLinked::class.java).toList())
            .contains(AccountLinked(owner, done.linkedAccountId))
        assertThat(fake.isOpen(sessionId)).isTrue()
        assertThat(fake.wasLoggedOut(sessionId)).isFalse()
        assertThat(attempts.find(owner)).isNull()
        assertThat(meters.counter("telex.linking.attempts", "outcome", "linked").count()).isGreaterThan(0.0)
        awaitSynced(done.linkedAccountId, 101)
    }

    private fun awaitSynced(
        id: LinkedAccountId,
        chats: Int,
    ) = await().atMost(Duration.ofSeconds(10)).untilAsserted {
        val row =
            jdbc.queryForMap(
                "SELECT chats_total, chat_sync_completed_at FROM linked_account WHERE id = ?",
                id.value,
            )
        assertThat(row["chats_total"]).isEqualTo(chats)
        assertThat(row["chat_sync_completed_at"]).isNotNull()
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM channel WHERE linked_account_id = ?", Int::class.java, id.value),
        ).isEqualTo(chats)
    }

    @Test
    fun `AC-117 a session Telegram ends right after the link ends as Session lost, not Connected`() {
        toCodeStep("9996670003")

        val done = finish() as LinkingProgress.Completed

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(rowsOf(owner).single { it["id"] == done.linkedAccountId.value }[STATE]).isEqualTo("session_lost")
        }
    }

    @Test
    fun `AC-01 an account without two-step verification skips the password and returns to where it started`() {
        toCodeStep("9996600102", origin = LinkingOrigin.ACCOUNTS)

        val done = finish() as LinkingProgress.Completed

        assertThat(done.outcome).isEqualTo(LinkingOutcome.LINKED)
        assertThat(done.origin).isEqualTo(LinkingOrigin.ACCOUNTS)
        assertThat(rowsOf(owner)).hasSize(1)
    }

    @Test
    fun `AC-04 an account of another Owner is refused, logged out and destroyed and the other row stays`() {
        val other = newOwner()
        val theirs = insertAccount(other, 9996600103L)
        val before = sessionDirectories()
        toCodeStep("9996600103")
        val sessionId = attemptSession()
        assertThat(sessionDirectories()).isEqualTo(before + 1)

        assertThat(refusal { finish() })
            .containsEntry(CODE, "telegram-account-owned-by-another-owner")
            .containsEntry("status", 409)

        assertThat(fake.wasLoggedOut(sessionId)).isTrue()
        assertThat(sessionDirectories()).isEqualTo(before)
        assertThat(rowsOf(owner)).isEmpty()
        assertThat(rows.getMine(other, theirs)!!.state).isEqualTo(LinkedAccountState.CONNECTED)
        assertThat(attempts.find(owner)).isNull()
        assertThat(meters.counter("telex.linking.attempts", "outcome", "refused_other_owner").count())
            .isGreaterThan(0.0)
    }

    @Test
    fun `AC-109 a sign-in Telegram authorized but teleX could not finish is logged out and discarded`() {
        val before = sessionDirectories()
        toCodeStep("9996600108")
        val sessionId = attemptSession()
        fake.failAfterAuthorization = true

        assertThat(refusal { finish() }).containsEntry(CODE, "telegram-unavailable")

        assertThat(fake.wasLoggedOut(sessionId)).isTrue()
        assertThat(sessionDirectories()).isEqualTo(before)
        assertThat(attempts.find(owner)).isNull()
        assertThat(rowsOf(owner)).isEmpty()
    }

    @Test
    fun `AC-108 the same account again while connected is refused and logged out, matched by identity`() {
        insertAccount(owner, 9996600104L)
        toCodeStep("9996600104")
        val sessionId = attemptSession()

        assertThat(refusal { finish() }).containsEntry(CODE, "telegram-account-already-linked")

        assertThat(fake.wasLoggedOut(sessionId)).isTrue()
        assertThat(rowsOf(owner)).hasSize(1)
        assertThat(attempts.find(owner)).isNull()
    }

    @Test
    fun `AC-115 an account that took the last place during the attempt is refused with the limit and logged out`() {
        insertAccount(owner, 9996600105L)
        toCodeStep("9996600106")
        val sessionId = attemptSession()
        insertAccount(owner, 9996600107L)

        assertThat(refusal { finish() })
            .containsEntry(CODE, "linked-account-limit-reached")
            .containsEntry("limit", 2)

        assertThat(fake.wasLoggedOut(sessionId)).isTrue()
        assertThat(rowsOf(owner)).hasSize(2)
    }

    @Test
    fun `AC-117 AC-108 signing in again brings back the same account without a new place or a link event`() {
        val oldSession = fake.open(ByteArray(32))
        val target = insertAccount(owner, 9996600108L, sessionId = oldSession)
        jdbc.update(
            "UPDATE linked_account SET state = 'session_lost', telegram_session_id = NULL, tdlib_key_sealed = NULL " +
                "WHERE id = ?",
            target.value,
        )
        insertAccount(owner, 9996600109L)
        toCodeStep("9996600108", target)
        val newSession = attemptSession()
        val before = sessionDirectories()

        val done = finish() as LinkingProgress.Completed

        assertThat(done.outcome).isEqualTo(LinkingOutcome.SIGNED_IN_AGAIN)
        assertThat(done.linkedAccountId).isEqualTo(target)
        val row = rowsOf(owner).single { it["id"] == target.value }
        assertThat(row[STATE]).isEqualTo("connected")
        assertThat(row[SESSION_ID]).isEqualTo(newSession.value)
        assertThat(row["display_name"]).isEqualTo("Test user 0108")
        assertThat(ownerKeys.open(owner, row["tdlib_key_sealed"] as ByteArray, target.keyAad())).hasSize(32)
        assertThat(rowsOf(owner)).hasSize(2)
        assertThat(sessionDirectories()).isEqualTo(before)
        assertThat(events.stream(LinkedAccountStateChanged::class.java).toList())
            .contains(LinkedAccountStateChanged(owner, target, LinkedAccountState.CONNECTED))
        assertThat(events.stream(AccountLinked::class.java).toList().map { it.linkedAccountId }).doesNotContain(target)
        awaitSynced(target, 108)
    }

    @Test
    fun `AC-117 signing in again destroys the old session directory of the account`() {
        val oldSession = fake.open(ByteArray(32))
        val target = insertAccount(owner, 9996600115L, sessionId = oldSession)
        jdbc.update("UPDATE linked_account SET state = 'session_lost' WHERE id = ?", target.value)
        jdbc.update(
            "UPDATE linked_account SET telegram_session_id = ?, tdlib_key_sealed = ? WHERE id = ?",
            oldSession.value,
            ByteArray(SEALED_BYTES) { 1 },
            target.value,
        )
        toCodeStep("9996600115", target)
        val before = sessionDirectories()

        finish()

        assertThat(sessionDirectories()).isEqualTo(before - 1)
        assertThat(fake.isOpen(oldSession)).isFalse()
    }

    @Test
    fun `AC-111 AC-117 signing in again for an account unlinked meanwhile ends as not found`() {
        val oldSession = fake.open(ByteArray(32))
        val target = insertAccount(owner, 9996600150L, sessionId = oldSession)
        jdbc.update(
            "UPDATE linked_account SET state = 'session_lost', telegram_session_id = NULL, tdlib_key_sealed = NULL " +
                "WHERE id = ?",
            target.value,
        )
        toCodeStep("9996600150", target)
        val newSession = attemptSession()
        val before = sessionDirectories()
        Mockito
            .doAnswer { call ->
                val holder = call.callRealMethod()
                jdbc.update("DELETE FROM linked_account WHERE id = ?", target.value)
                holder
            }.`when`(rows)
            .findByTelegramUser(Mockito.anyLong())

        assertThat(refusal { finish() })
            .containsEntry(CODE, "linking-attempt-not-found")
            .containsEntry("status", 404)

        assertThat(rowsOf(owner)).isEmpty()
        assertThat(events.stream(LinkedAccountStateChanged::class.java).toList().map { it.linkedAccountId })
            .doesNotContain(target)
        assertThat(fake.wasLoggedOut(newSession)).isTrue()
        assertThat(fake.isOpen(newSession)).isFalse()
        assertThat(sessionDirectories()).isEqualTo(before - 1)
        assertThat(attempts.find(owner)).isNull()
    }

    @Test
    fun `AC-04 an untargeted add of an account unlinked meanwhile links it as new instead of refusing`() {
        val oldSession = fake.open(ByteArray(32))
        val gone = insertAccount(owner, 9996600151L, sessionId = oldSession)
        jdbc.update(
            "UPDATE linked_account SET state = 'session_lost', telegram_session_id = NULL, tdlib_key_sealed = NULL " +
                "WHERE id = ?",
            gone.value,
        )
        toCodeStep("9996600151")
        val newSession = attemptSession()
        Mockito
            .doAnswer { call ->
                val holder = call.callRealMethod()
                jdbc.update("DELETE FROM linked_account WHERE id = ?", gone.value)
                holder
            }.`when`(rows)
            .findByTelegramUser(Mockito.anyLong())

        val done = finish() as LinkingProgress.Completed

        assertThat(done.outcome).isEqualTo(LinkingOutcome.LINKED)
        val row = rowsOf(owner).single()
        assertThat(row["id"]).isEqualTo(done.linkedAccountId.value)
        assertThat(row["id"]).isNotEqualTo(gone.value)
        assertThat(row[SESSION_ID]).isEqualTo(newSession.value)
        assertThat(fake.isOpen(newSession)).isTrue()
        assertThat(fake.wasLoggedOut(newSession)).isFalse()
    }

    @Test
    fun `AC-117 signing in again with a different Telegram account is refused and the target stays Session lost`() {
        val target = insertAccount(owner, 9996600110L, LinkedAccountState.SESSION_LOST)
        toCodeStep("9996600111", target)
        val sessionId = attemptSession()

        assertThat(refusal { finish() }).containsEntry(CODE, "telegram-account-mismatch")

        assertThat(fake.wasLoggedOut(sessionId)).isTrue()
        assertThat(rows.getMine(owner, target)!!.state).isEqualTo(LinkedAccountState.SESSION_LOST)
        assertThat(rowsOf(owner)).hasSize(1)
    }

    @Test
    fun `AC-117 AC-04 signing in again with another Owner's account shows the one-Owner rule`() {
        val target = insertAccount(owner, 9996600112L, LinkedAccountState.SESSION_LOST)
        insertAccount(newOwner(), 9996600113L)
        toCodeStep("9996600113", target)

        assertThat(refusal { finish() }).containsEntry(CODE, "telegram-account-owned-by-another-owner")

        assertThat(rows.getMine(owner, target)!!.state).isEqualTo(LinkedAccountState.SESSION_LOST)
    }

    @Test
    fun `AC-115 signing in again takes no new place when the Owner is at the limit`() {
        val target = insertAccount(owner, 9996600116L, LinkedAccountState.SESSION_LOST)
        insertAccount(owner, 9996600117L)
        toCodeStep("9996600116", target)

        val done = finish() as LinkingProgress.Completed

        assertThat(done.outcome).isEqualTo(LinkingOutcome.SIGNED_IN_AGAIN)
        assertThat(rowsOf(owner)).hasSize(2)
    }

    @Test
    fun `AC-04 the second Owner finishing with a Telegram account just taken is logged out`() {
        val other = newOwner()
        val otherSession = signInSessions.start(other, null, null, null, false).sessionId
        toCodeStep("9996600114")
        linking.start(other, otherSession, LinkingOrigin.INBOX, null)
        linking.submitPhone(other, otherSession, "9996600114")
        val mine = attemptSession()
        val theirs = attempts.find(other)!!.sessionId

        val first = runCatching { finish() }
        val second = runCatching { linking.submitCode(other, otherSession, FakeTelegram.CODE) }

        assertThat(listOf(first, second).count { it.isSuccess }).isEqualTo(1)
        assertThat(fake.wasLoggedOut(if (first.isSuccess) theirs else mine)).isTrue()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM linked_account WHERE telegram_user_id = 9996600114",
                Int::class.java,
            ),
        ).isEqualTo(1)
    }

    /**
     * Makes the first two reads of "who holds this Telegram user" meet before either returns, so both completions
     * see no holder and both go on to insert: the unique index, not the earlier read, decides the winner.
     */
    private fun meetAfterTheHolderRead() {
        val met = CountDownLatch(2)
        Mockito
            .doAnswer { call ->
                val holder = call.callRealMethod()
                met.countDown()
                met.await(RACE_SECONDS, TimeUnit.SECONDS)
                holder
            }.`when`(rows)
            .findByTelegramUser(Mockito.anyLong())
    }

    private fun <T> concurrently(vararg jobs: () -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(jobs.size)
        try {
            val go = CountDownLatch(1)
            val futures = jobs.map { job -> pool.submit<Result<T>> { go.await().let { runCatching(job) } } }
            go.countDown()
            return futures.map { it.get(RACE_SECONDS * 2, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `AC-04 two Owners completing the same Telegram account together leave one row and log the loser out`() {
        val other = newOwner()
        val otherSession = signInSessions.start(other, null, null, null, false).sessionId
        toCodeStep("9996600120")
        linking.start(other, otherSession, LinkingOrigin.INBOX, null)
        linking.submitPhone(other, otherSession, "9996600120")
        val mine = attemptSession()
        val theirs = attempts.find(other)!!.sessionId
        meetAfterTheHolderRead()

        val results =
            concurrently(
                { linking.submitCode(owner, session, FakeTelegram.CODE) },
                { linking.submitCode(other, otherSession, FakeTelegram.CODE) },
            )

        val winner = results.indexOfFirst { it.isSuccess }
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val loser = results[1 - winner].exceptionOrNull()
        assertThat(loser).isInstanceOf(DomainProblem::class.java)
        assertThat(
            (loser as DomainProblem).body.properties,
        ).containsEntry(CODE, "telegram-account-owned-by-another-owner")
        // the lost insert ran the afterRace branch rather than the early read refusing it
        assertThat(
            Mockito.mockingDetails(AopTestUtils.getUltimateTargetObject<LinkCompletion>(completion)).invocations.count {
                it.method.name.startsWith("afterRace")
            },
        ).isEqualTo(1)
        assertThat(fake.wasLoggedOut(if (winner == 0) theirs else mine)).isTrue()
        assertThat(fake.wasLoggedOut(if (winner == 0) mine else theirs)).isFalse()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM linked_account WHERE telegram_user_id = 9996600120",
                Int::class.java,
            ),
        ).isEqualTo(1)
        assertThat(attempts.find(owner)).isNull()
        assertThat(attempts.find(other)).isNull()
        linking.cancel(other)
    }

    @Test
    fun `AC-115 four completions of one Owner racing a limit of two link exactly two`() {
        val sessionsOpened = (1..RACERS).map { fake.open(ByteArray(KEY_BYTES)) }
        val users = (1..RACERS).map { TelegramUser(9996600130L + it, "Racer $it", "99", "3$it") }

        val results =
            concurrently(
                *(0 until RACERS)
                    .map { i ->
                        { completion.complete(owner, null, sessionsOpened[i], ByteArray(KEY_BYTES), users[i]) }
                    }.toTypedArray(),
            ).map { it.getOrThrow() }

        assertThat(results.filterIsInstance<telex.messaging.internal.account.Completion.Linked>()).hasSize(2)
        assertThat(results.filterIsInstance<telex.messaging.internal.account.Completion.Refused>().map { it.reason })
            .containsOnly(telex.messaging.internal.account.LinkRefusal.LIMIT)
        assertThat(rowsOf(owner)).hasSize(2)
    }

    companion object {
        private const val RACE_SECONDS = 5L
        private const val RACERS = 4
        private const val KEY_BYTES = 32

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.adapter") { "fake" }
            registry.add("telex.telegram.sessions-dir") { SESSIONS_DIR.toString() }
            registry.add("telex.telegram.api-id") { "12345" }
            registry.add("telex.telegram.api-hash") { "abcdef" }
            registry.add("telex.telegram.max-accounts-per-owner") { "2" }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
        }
    }
}
