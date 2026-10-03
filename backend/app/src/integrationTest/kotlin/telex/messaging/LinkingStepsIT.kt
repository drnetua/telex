package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.messaging.internal.attempt.LinkingAttempts
import telex.shared.DomainProblem
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.listDirectoryEntries

private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-linking-steps")
private const val START = "2026-10-02T10:00:00Z"
private const val PLAIN = "9996600005"
private const val HINT = "9996610005"
private const val NO_HINT = "9996620005"
private const val BANNED = "9996630005"
private const val UNREGISTERED = "9996640005"
private const val FLOOD_PHONE = "9996650005"
private const val FLOOD_CODE = "9996660005"
private const val FLOOD_PASSWORD = "9996680005"
private const val CODE = "code"
private const val STEP = "step"

/** AC-02 (code refusals and waits), AC-106 (password), AC-107 (phone refusals) on the in-memory Telegram. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class LinkingStepsIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var attempts: LinkingAttempts

    @Autowired lateinit var telegram: TelegramSessions

    @Autowired lateinit var meters: MeterRegistry

    private var owner = OwnerId(UUID.randomUUID())
    private var session = SignInSessionId(UUID.randomUUID())

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse(START))
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        owner = OwnerId(id)
        session = signInSessions.start(owner, null, null, null, false).sessionId
        linking.start(owner, session, LinkingOrigin.INBOX, null)
    }

    @AfterEach
    fun cleanUp() {
        linking.cancel(owner)
        // an authorized attempt now persists its account, and the next test reuses the same test phones
        jdbc.update("DELETE FROM linked_account WHERE owner_id = ?", owner.value)
    }

    private fun step() = linking.get(owner).step

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

    private fun toCodeStep(phone: String = PLAIN) = linking.submitPhone(owner, session, phone)

    private fun restart() {
        linking.cancel(owner)
        linking.start(owner, session, LinkingOrigin.INBOX, null)
    }

    private fun sessionDirectories() = SESSIONS_DIR.listDirectoryEntries().size

    @Test
    fun `a phone with formatting is reduced to digits and moves to the code step with its length`() {
        val progress = toCodeStep("+999 66 0 0005") as LinkingProgress.Step

        assertThat(progress.attempt.step).isEqualTo(LinkingStep.CODE)
        assertThat(progress.attempt.codeLength).isEqualTo(5)
        assertThat(step()).isEqualTo(LinkingStep.CODE)
    }

    @Test
    fun `AC-107 a phone without digits or not a Telegram number is invalid and the attempt stays at the phone step`() {
        listOf("+ ()", "12345").forEach { phone ->
            assertThat(refusal { linking.submitPhone(owner, session, phone) })
                .containsEntry(CODE, "telegram-phone-invalid")
        }
        assertThat(step()).isEqualTo(LinkingStep.PHONE)
    }

    @Test
    fun `AC-107 a number without a Telegram account or a banned one is refused and the attempt stays`() {
        assertThat(refusal { toCodeStep(UNREGISTERED) }).containsEntry(CODE, "telegram-phone-unregistered")
        assertThat(refusal { toCodeStep(BANNED) }).containsEntry(CODE, "telegram-phone-banned")
        assertThat(step()).isEqualTo(LinkingStep.PHONE)
        assertThat(toCodeStep()).isInstanceOf(LinkingProgress.Step::class.java)
    }

    @Test
    fun `AC-02 a wrong or expired code is refused and the attempt stays at the code step, a new code can be asked`() {
        toCodeStep()

        assertThat(refusal { linking.submitCode(owner, session, "11111") }).containsEntry(CODE, "telegram-code-wrong")
        assertThat(refusal { linking.submitCode(owner, session, FakeTelegram.EXPIRED_CODE) })
            .containsEntry(CODE, "telegram-code-expired")
        assertThat(step()).isEqualTo(LinkingStep.CODE)
        val again = linking.resendCode(owner, session) as LinkingProgress.Step
        assertThat(again.attempt.step).isEqualTo(LinkingStep.CODE)
        assertThat(again.attempt.codeLength).isEqualTo(5)
    }

    @Test
    fun `AC-02 a wait on the phone step ends the attempt and destroys its session, with the retry time`() {
        val before = sessionDirectories()

        val thrown = refusal { toCodeStep(FLOOD_PHONE) }

        assertThat(thrown).containsEntry(CODE, "telegram-wait-required").containsEntry("status", 429)
        assertThat(thrown).containsEntry("retryAt", "2026-10-02T10:00:30Z")
        assertThat(sessionDirectories()).isEqualTo(before - 1)
        assertThat(refusal { linking.get(owner) }).containsEntry(CODE, "linking-attempt-not-found")
    }

    @Test
    fun `AC-02 a wait on the code step ends the attempt`() {
        toCodeStep(FLOOD_CODE)

        assertThat(refusal { linking.submitCode(owner, session, FakeTelegram.CODE) })
            .containsEntry(CODE, "telegram-wait-required")
        assertThat(refusal { linking.get(owner) }).containsEntry(CODE, "linking-attempt-not-found")
    }

    @Test
    fun `a right code moves to the password step with the hint, or hands over the authorized account`() {
        toCodeStep(HINT)
        val next = linking.submitCode(owner, session, FakeTelegram.CODE) as LinkingProgress.Step
        assertThat(next.attempt.step).isEqualTo(LinkingStep.PASSWORD)
        assertThat(next.attempt.passwordHint).isEqualTo(FakeTelegram.PASSWORD_HINT)

        restart()
        toCodeStep(PLAIN)
        assertThat(linking.submitCode(owner, session, FakeTelegram.CODE))
            .isInstanceOf(LinkingProgress.Completed::class.java)
    }

    @Test
    fun `AC-106 a wrong password carries the hint, none when unset, and the attempt stays at the password step`() {
        toCodeStep(HINT)
        linking.submitCode(owner, session, FakeTelegram.CODE)
        assertThat(refusal { linking.submitPassword(owner, session, "nope") })
            .containsEntry(CODE, "telegram-password-wrong")
            .containsEntry("passwordHint", FakeTelegram.PASSWORD_HINT)
        assertThat(step()).isEqualTo(LinkingStep.PASSWORD)

        restart()
        toCodeStep(NO_HINT)
        val next = linking.submitCode(owner, session, FakeTelegram.CODE) as LinkingProgress.Step
        assertThat(next.attempt.passwordHint).isNull()
        assertThat(refusal { linking.submitPassword(owner, session, "nope") }).containsEntry("passwordHint", null)
    }

    @Test
    fun `a right password hands over the authorized account`() {
        toCodeStep(HINT)
        linking.submitCode(owner, session, FakeTelegram.CODE)

        assertThat(linking.submitPassword(owner, session, FakeTelegram.PASSWORD))
            .isInstanceOf(LinkingProgress.Completed::class.java)
    }

    @Test
    fun `AC-02 a wait on the password step ends the attempt`() {
        toCodeStep(FLOOD_PASSWORD)
        linking.submitCode(owner, session, FakeTelegram.CODE)

        assertThat(refusal { linking.submitPassword(owner, session, "x") })
            .containsEntry(CODE, "telegram-wait-required")
        assertThat(refusal { linking.get(owner) }).containsEntry(CODE, "linking-attempt-not-found")
    }

    @Test
    fun `a step for another step is a mismatch carrying the current step and changes nothing`() {
        assertThat(refusal { linking.submitCode(owner, session, FakeTelegram.CODE) })
            .containsEntry(CODE, "linking-step-mismatch")
            .containsEntry(STEP, "phone")
        assertThat(refusal { linking.submitPassword(owner, session, "x") }).containsEntry(STEP, "phone")
        toCodeStep()
        assertThat(refusal { linking.submitPhone(owner, session, PLAIN) }).containsEntry(STEP, "code")
        assertThat(refusal { linking.resendCode(OwnerId(UUID.randomUUID()), session) })
            .containsEntry(CODE, "linking-attempt-not-found")
        assertThat(step()).isEqualTo(LinkingStep.CODE)
    }

    @Test
    fun `a step after the Sign-in Session ended discards the attempt`() {
        val before = sessionDirectories()
        signInSessions.endMine(owner, session)

        assertThat(refusal { toCodeStep() }).containsEntry(CODE, "linking-attempt-not-found")
        assertThat(sessionDirectories()).isEqualTo(before - 1)
    }

    @Test
    fun `Telegram not answering is unavailable and the attempt stays at its step`() {
        val fake = telegram as FakeTelegram
        val id = attempts.find(owner)!!.sessionId
        fake.dropConnectivity(id)

        assertThat(refusal { toCodeStep() }).containsEntry(CODE, "telegram-unavailable").containsEntry("status", 503)

        fake.restoreConnectivity(id)
        assertThat(step()).isEqualTo(LinkingStep.PHONE)
        assertThat(toCodeStep()).isInstanceOf(LinkingProgress.Step::class.java)
    }

    @Test
    fun `a step resets the inactivity timer`() {
        clock.advance(Duration.ofMinutes(14))
        toCodeStep()
        clock.advance(Duration.ofMinutes(14))

        assertThat(step()).isEqualTo(LinkingStep.CODE)
    }

    @Test
    fun `step durations are recorded and well under the three second target on the fake`() {
        toCodeStep(HINT)
        linking.submitCode(owner, session, FakeTelegram.CODE)
        linking.submitPassword(owner, session, FakeTelegram.PASSWORD)

        listOf("phone", "code", "password").forEach { name ->
            val timer = meters.find("telex.linking.step.duration").tag(STEP, name).timer()
            assertThat(timer).describedAs(name).isNotNull
            assertThat(timer!!.max(TimeUnit.MILLISECONDS)).isLessThan(3_000.0)
        }
    }

    @Test
    fun `a wait counts as a flood_wait outcome`() {
        val before = meters.counter("telex.linking.attempts", "outcome", "flood_wait").count()

        refusal { toCodeStep(FLOOD_PHONE) }

        assertThat(meters.counter("telex.linking.attempts", "outcome", "flood_wait").count()).isEqualTo(before + 1)
    }

    companion object {
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
