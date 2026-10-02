package telex.mail

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Captures sent emails; can be told to fail. Import with `@Import(RecordingMailerConfiguration::class)`. */
class RecordingMailer : Mailer {
    val sent = CopyOnWriteArrayList<OutgoingEmail>()

    /** Every send attempt, including the ones that failed. */
    val attempts = AtomicInteger()

    @Volatile
    var failing = false

    override fun send(email: OutgoingEmail) {
        attempts.incrementAndGet()
        if (failing) throw MailUnavailable()
        sent += email
    }

    fun reset() {
        sent.clear()
        attempts.set(0)
        failing = false
    }
}

@TestConfiguration
class RecordingMailerConfiguration {
    @Bean
    @Primary
    fun recordingMailer() = RecordingMailer()
}
