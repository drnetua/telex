package telex.mail

/** `template` is a tag for the metric only. */
data class OutgoingEmail(
    val to: String,
    val subject: String,
    val text: String,
    val template: String,
)

/** Thrown when the mail server refuses or times out, so the caller's transaction rolls back. */
class MailUnavailable(
    cause: Throwable? = null,
) : RuntimeException("Mail server unavailable", cause)

interface Mailer {
    @Throws(MailUnavailable::class)
    fun send(email: OutgoingEmail)
}
