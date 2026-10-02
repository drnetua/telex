package telex.identity

import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mail.javamail.JavaMailSenderImpl
import telex.TestcontainersConfiguration
import telex.shared.DomainProblem

/** A checked MessagingException via the real SmtpMailer: 503, no grant stored, earlier grant live (AC-103). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, SignInMailFailureIT.ThrowingSender::class)
class SignInMailFailureIT {
    class FailingSender : JavaMailSenderImpl() {
        @Volatile var failing = false

        override fun send(mimeMessage: MimeMessage) {
            if (failing) throw MessagingException("checked failure")
        }
    }

    @TestConfiguration
    class ThrowingSender {
        @Bean
        @Primary
        fun failingSender() = FailingSender()
    }

    @Autowired lateinit var signIn: SignIn

    @Autowired lateinit var sender: FailingSender

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun reset() {
        sender.failing = false
        jdbc.execute("DELETE FROM sign_in_grant")
    }

    @AfterEach
    fun heal() {
        sender.failing = false
    }

    @Test
    fun `a checked send failure answers 503, stores no grant and keeps the earlier grant live`() {
        val first = signIn.request("anton@mail.com")
        sender.failing = true
        val t = runCatching { signIn.request("anton@mail.com") }.exceptionOrNull()

        assertThat(t).isInstanceOf(DomainProblem::class.java)
        assertThat((t as DomainProblem).statusCode.value()).isEqualTo(503)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sign_in_grant", Int::class.java)).isEqualTo(1)
        assertThat(
            jdbc.queryForObject(
                "SELECT superseded_at IS NULL FROM sign_in_grant WHERE id = ?",
                Boolean::class.java,
                first.grantId.value,
            ),
        ).isTrue()
    }
}
