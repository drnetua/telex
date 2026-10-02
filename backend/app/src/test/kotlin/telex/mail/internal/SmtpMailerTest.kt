package telex.mail.internal

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import telex.mail.MailUnavailable
import telex.mail.OutgoingEmail
import java.util.Properties

class SmtpMailerTest {
    private val sender = mock(JavaMailSender::class.java)
    private val meters = SimpleMeterRegistry()
    private val mailer = SmtpMailer(sender, meters, "teleX <no-reply@localhost>")
    private val email = OutgoingEmail("owner@example.com", "Sign in to teleX", "Open the link", "sign-in")

    private fun counter(outcome: String) =
        meters
            .find("telex.mail.sent")
            .tags("template", "sign-in", "outcome", outcome)
            .counter()
            ?.count()

    @Test
    fun `send hands one message to the JavaMailSender and counts it as sent`() {
        val message = MimeMessage(Session.getInstance(Properties()))
        `when`(sender.createMimeMessage()).thenReturn(message)

        mailer.send(email)

        verify(sender).send(message)
        assertEquals("Sign in to teleX", message.subject)
        assertEquals(1.0, counter("sent"))
    }

    @Test
    fun `send maps a refused or timed out server to MailUnavailable and counts it as failed`() {
        `when`(sender.createMimeMessage()).thenReturn(MimeMessage(Session.getInstance(Properties())))
        doThrow(MailSendException("connection refused")).`when`(sender).send(any(MimeMessage::class.java))

        val failure = assertThrows<MailUnavailable> { mailer.send(email) }

        assertEquals(1.0, counter("failed"))
        assertEquals(false, failure.message.orEmpty().contains("owner@example.com"))
    }
}
