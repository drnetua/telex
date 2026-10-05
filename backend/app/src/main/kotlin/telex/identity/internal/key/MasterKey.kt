package telex.identity.internal.key

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64

/** The installation master key (`TELEX_MASTER_KEY`: 32 random bytes, base64), or absent when not configured. */
@Component
class MasterKey(
    @Value($$"${telex.master-key:}") raw: String,
) {
    private val bytes: ByteArray? =
        raw.trim().takeIf { it.isNotEmpty() }?.let { text ->
            val decoded =
                runCatching { Base64.getDecoder().decode(text) }
                    .getOrElse { throw IllegalStateException(INVALID, it) }
            check(decoded.size == KEY_BYTES) { INVALID }
            decoded
        }

    val configured: Boolean get() = bytes != null

    /** The key bytes; never logged or put in a message. */
    fun bytes(): ByteArray = checkNotNull(bytes) { "TELEX_MASTER_KEY is not set" }

    override fun toString(): String = "MasterKey(configured=$configured)"

    private companion object {
        const val KEY_BYTES = 32
        const val INVALID = "TELEX_MASTER_KEY must be 32 random bytes, base64-encoded"
    }
}
