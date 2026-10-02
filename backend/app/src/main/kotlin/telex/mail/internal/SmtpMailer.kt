package telex.mail.internal

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import telex.mail.MailUnavailable
import telex.mail.Mailer
import telex.mail.OutgoingEmail

@Component
internal class SmtpMailer(
    private val sender: JavaMailSender,
    private val meters: MeterRegistry,
    @Value("\${telex.mail.from}") private val from: String,
) : Mailer {
    override fun send(email: OutgoingEmail) {
        try {
            val message = sender.createMimeMessage()
            MimeMessageHelper(message, "UTF-8").apply {
                setFrom(from)
                setTo(email.to)
                setSubject(email.subject)
                setText(email.text, false)
            }
            sender.send(message)
        } catch (e: MailException) {
            count(email, "failed")
            throw MailUnavailable(e)
        }
        count(email, "sent")
    }

    private fun count(
        email: OutgoingEmail,
        outcome: String,
    ) {
        meters.counter("telex.mail.sent", "template", email.template, "outcome", outcome).increment()
    }
}
