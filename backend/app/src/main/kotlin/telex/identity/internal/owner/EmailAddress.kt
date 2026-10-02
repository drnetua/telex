package telex.identity.internal.owner

import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import java.util.Locale

/** A complete email address: [asTyped] as the person wrote it, [canonical] for account identity (AC-34). */
class EmailAddress private constructor(
    val asTyped: String,
    val canonical: String,
) {
    companion object {
        private const val MAX_LENGTH = 254
        private val PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /**
         * The mail library must accept it too, or sending would fail after the grant is stored, and it must read it
         * back as exactly this one plain mailbox: `Name<a@b.com>` would be keyed by the text but mailed to `a@b.com`.
         */
        private fun mailable(raw: String): Boolean =
            try {
                val parsed = InternetAddress(raw, true)
                parsed.validate()
                parsed.address == raw && parsed.personal == null && !parsed.isGroup
            } catch (_: AddressException) {
                false
            }

        fun parse(raw: String): EmailAddress? {
            val local = raw.substringBefore('@').substringBefore('+')
            val valid = raw.length <= MAX_LENGTH && PATTERN.matches(raw) && local.isNotEmpty() && mailable(raw)
            return if (valid) {
                EmailAddress(raw, (local + "@" + raw.substringAfter('@')).lowercase(Locale.ROOT))
            } else {
                null
            }
        }
    }
}
