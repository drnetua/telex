package telex.identity.internal.owner

import java.util.Locale

/** A complete email address: [asTyped] as the person wrote it, [canonical] for account identity (AC-34). */
class EmailAddress private constructor(
    val asTyped: String,
    val canonical: String,
) {
    companion object {
        private const val MAX_LENGTH = 254
        private val PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun parse(raw: String): EmailAddress? {
            val local = raw.substringBefore('@').substringBefore('+')
            val valid = raw.length <= MAX_LENGTH && PATTERN.matches(raw) && local.isNotEmpty()
            return if (valid) {
                EmailAddress(raw, (local + "@" + raw.substringAfter('@')).lowercase(Locale.ROOT))
            } else {
                null
            }
        }
    }
}
