package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.mail.OutgoingEmail
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import java.time.Instant

/** AC-98: the "New sign-in to teleX" email goes out after commit, except for the sign-in that creates the account. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
class NewSignInNoticeIT {
    @Autowired lateinit var signIn: SignIn

    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val safariIPhone =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        mailer.reset()
        jdbc.execute("DELETE FROM event_publication")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM sign_in_grant")
        jdbc.execute("DELETE FROM owner")
    }

    private fun tokenFromLastMail(): String = Regex("#([A-Za-z0-9_-]+)").find(mailer.sent.last().text)!!.groupValues[1]

    private fun signInByLink(
        email: String,
        userAgent: String?,
        zone: String?,
    ): SignedIn {
        signIn.request(email)
        return signIn.redeemByLink(tokenFromLastMail(), null, userAgent, zone)
    }

    private fun notices(): List<OutgoingEmail> = mailer.sent.filter { it.template == "new-sign-in" }

    private fun awaitNotice(): OutgoingEmail {
        val deadline = System.currentTimeMillis() + 5_000
        while (notices().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertThat(notices()).describedAs("New sign-in emails").hasSize(1)
        return notices().single()
    }

    @Test
    fun `AC-98 a later sign-in emails the address the account was created with`() {
        assertThat(signInByLink("anton@mail.com", null, null).createdAccount).isTrue()
        Thread.sleep(500)
        assertThat(notices()).describedAs("no email for the sign-in that creates the account").isEmpty()

        assertThat(signInByLink("Anton+work@Mail.com", safariIPhone, "Europe/Kyiv").createdAccount).isFalse()
        val mail = awaitNotice()

        assertThat(mail.to).isEqualTo("anton@mail.com")
        assertThat(mail.subject).isEqualTo("New sign-in to teleX")
        assertThat(mail.text)
            .contains("Safari on iPhone")
            .contains("phone")
            .contains("17:00")
            .contains("Europe/Kyiv")
            .contains("14:00")
            .contains("UTC")
            .contains("http://localhost:8080/profile#sessions")
    }

    @Test
    fun `AC-98 a session without a time zone shows the local time as UTC`() {
        signInByLink("anton@mail.com", null, null)
        signInByLink("anton@mail.com", safariIPhone, "UTC")
        val mail = awaitNotice()
        assertThat(mail.text).contains("(UTC)").contains("14:00")
    }

    @Test
    fun `AC-98 a failed send leaves the publication incomplete`() {
        signInByLink("anton@mail.com", null, null)
        signIn.request("anton@mail.com")
        val token = tokenFromLastMail()
        mailer.failing = true
        assertThat(signIn.redeemByLink(token, null, safariIPhone, "Europe/Kyiv").createdAccount).isFalse()
        Thread.sleep(1_000)
        val incomplete =
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL " +
                    "AND event_type LIKE '%SignInSessionStarted'",
                Int::class.java,
            )
        assertThat(incomplete).isGreaterThanOrEqualTo(1)
        assertThat(notices()).isEmpty()
    }
}
