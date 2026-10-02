package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import telex.shared.DomainProblem
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** AC-34, AC-82, AC-35, AC-84, AC-85, AC-103, AC-104: redeeming a Sign-in Link or Code. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
class SignInRedeemIT {
    @Autowired lateinit var signIn: SignIn

    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val start = Instant.parse("2026-10-02T14:00:00Z")

    @BeforeEach
    fun reset() {
        clock.set(start)
        mailer.reset()
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM sign_in_grant")
        jdbc.execute("DELETE FROM owner")
    }

    private class Mail(
        val grantId: SignInGrantId,
        val token: String,
        val code: String,
    )

    private fun requestMail(email: String = "anton@mail.com"): Mail {
        val issued = signIn.request(email)
        val text = mailer.sent.last().text
        return Mail(
            issued.grantId,
            Regex("#([A-Za-z0-9_-]+)").find(text)!!.groupValues[1],
            Regex("\\b(\\d{6})\\b").find(text)!!.groupValues[1],
        )
    }

    private fun wrongCode(code: String) = if (code == "000000") "000001" else "000000"

    private fun problem(block: () -> Unit): DomainProblem {
        val t = runCatching(block).exceptionOrNull()
        assertThat(t).isInstanceOf(DomainProblem::class.java)
        return t as DomainProblem
    }

    private fun DomainProblem.code() = body.properties?.get("code")

    private fun ownerCount() = jdbc.queryForObject("SELECT count(*) FROM owner", Int::class.java)

    private fun liveSessions() =
        jdbc.queryForObject("SELECT count(*) FROM sign_in_session WHERE ended_at IS NULL", Int::class.java)

    @Test
    fun `AC-34 first link redeem creates the owner and a session, same canonical address reuses it`() {
        val mail = requestMail("anton@mail.com")
        val first = signIn.redeemByLink(mail.token, null, "Mozilla/5.0 Firefox/130.0", "Europe/Kyiv")
        assertThat(first.createdAccount).isTrue()
        assertThat(sessions.resolve(first.key, background = false)).isInstanceOf(SessionResolution.Live::class.java)
        assertThat(ownerCount()).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT email FROM owner", String::class.java)).isEqualTo("anton@mail.com")

        val again = requestMail("Anton+work@Mail.com")
        val second = signIn.redeemByLink(again.token, null, null, null)
        assertThat(second.createdAccount).isFalse()
        assertThat(ownerCount()).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT email FROM owner", String::class.java)).isEqualTo("anton@mail.com")
    }

    @Test
    fun `AC-82 redeeming by code signs in and kills the link`() {
        val mail = requestMail()
        val signedIn = signIn.redeemByCode(mail.grantId, mail.code, null, null, null)
        assertThat(signedIn.createdAccount).isTrue()
        val p = problem { signIn.redeemByLink(mail.token, null, null, null) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-used")
    }

    @Test
    fun `AC-84 a used link or code is refused as used`() {
        val mail = requestMail()
        signIn.redeemByLink(mail.token, null, null, null)
        assertThat(problem { signIn.redeemByLink(mail.token, null, null, null) }.code()).isEqualTo("sign-in-link-used")
        val p = problem { signIn.redeemByCode(mail.grantId, mail.code, null, null, null) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-used")
    }

    @Test
    fun `AC-35 redeem is checked at confirm time - 14 59 works and 15 01 is expired`() {
        val ok = requestMail()
        clock.set(start.plus(Duration.ofMinutes(14)).plusSeconds(59))
        assertThat(signIn.redeemByLink(ok.token, null, null, null).key).isNotBlank()

        clock.set(start)
        val late = requestMail()
        assertThat(signIn.preview(late.token).email).isEqualTo("anton@mail.com")
        clock.set(start.plus(Duration.ofMinutes(15)).plusSeconds(1))
        val p = problem { signIn.redeemByLink(late.token, null, null, null) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-expired")
        assertThat(problem { signIn.redeemByCode(late.grantId, late.code, null, null, null) }.code())
            .isEqualTo("sign-in-link-expired")
    }

    @Test
    fun `AC-85 five wrong codes void the grant and the right code and the link are refused`() {
        val mail = requestMail()
        listOf(4, 3, 2, 1).forEach { left ->
            val p = problem { signIn.redeemByCode(mail.grantId, wrongCode(mail.code), null, null, null) }
            assertThat(p.statusCode.value()).isEqualTo(422)
            assertThat(p.code()).isEqualTo("sign-in-code-wrong")
            assertThat(p.body.properties?.get("attemptsLeft")).isEqualTo(left)
        }
        val fifth = problem { signIn.redeemByCode(mail.grantId, wrongCode(mail.code), null, null, null) }
        assertThat(fifth.statusCode.value()).isEqualTo(410)
        assertThat(fifth.code()).isEqualTo("sign-in-grant-void")
        assertThat(problem { signIn.redeemByCode(mail.grantId, mail.code, null, null, null) }.code())
            .isEqualTo("sign-in-grant-void")
        assertThat(problem { signIn.redeemByLink(mail.token, null, null, null) }.code())
            .isEqualTo("sign-in-grant-void")
        assertThat(ownerCount()).isZero()
    }

    @Test
    fun `AC-103 an earlier email's link and code are refused as expired`() {
        val first = requestMail()
        val second = requestMail()
        assertThat(problem { signIn.redeemByLink(first.token, null, null, null) }.code())
            .isEqualTo("sign-in-link-expired")
        assertThat(problem { signIn.redeemByCode(first.grantId, first.code, null, null, null) }.code())
            .isEqualTo("sign-in-link-expired")
        val unknown = problem { signIn.redeemByCode(SignInGrantId(UUID.randomUUID()), "123456", null, null, null) }
        assertThat(unknown.code()).isEqualTo("sign-in-link-expired")
        assertThat(unknown.body.properties?.get("email")).isNull()
        assertThat(signIn.redeemByLink(second.token, null, null, null).key).isNotBlank()
    }

    @Test
    fun `AC-104 redeeming in a signed-in browser ends the held session and starts a new one`() {
        val held = signIn.redeemByLink(requestMail("a@mail.com").token, null, null, null)
        val mail = requestMail("b@mail.com")
        val next = signIn.redeemByCode(mail.grantId, mail.code, held.key, null, null)
        assertThat(sessions.resolve(held.key, background = false)).isEqualTo(SessionResolution.Ended)
        assertThat(sessions.resolve(next.key, background = false)).isInstanceOf(SessionResolution.Live::class.java)
        assertThat(ownerCount()).isEqualTo(2)
        assertThat(liveSessions()).isEqualTo(1)
    }

    @Test
    fun `two concurrent redeems of one grant produce exactly one sign-in`() {
        val mail = requestMail()
        val pool = Executors.newFixedThreadPool(2)
        val results =
            (1..2)
                .map { pool.submit(Callable { runCatching { signIn.redeemByLink(mail.token, null, null, null) } }) }
                .map { it.get() }
        pool.shutdown()
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        val failure = results.single { it.isFailure }.exceptionOrNull() as DomainProblem
        assertThat(failure.code()).isEqualTo("sign-in-link-used")
        assertThat(ownerCount()).isEqualTo(1)
        assertThat(liveSessions()).isEqualTo(1)
    }
}
