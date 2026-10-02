package telex.mail

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/** Captures sent emails; can be told to fail. Import with `@Import(RecordingMailerConfiguration::class)`. */
class RecordingMailer : Mailer {
    val sent = CopyOnWriteArrayList<OutgoingEmail>()

    @Volatile
    var failing = false

    override fun send(email: OutgoingEmail) {
        if (failing) throw MailUnavailable()
        sent += email
    }

    fun reset() {
        sent.clear()
        failing = false
    }
}

@TestConfiguration
class RecordingMailerConfiguration {
    @Bean
    @Primary
    fun recordingMailer() = RecordingMailer()
}
