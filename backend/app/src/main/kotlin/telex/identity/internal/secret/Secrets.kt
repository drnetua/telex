package telex.identity.internal.secret

import telex.identity.SignInGrantId
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Secret generation and hashing. Secrets are only ever stored as SHA-256 digests. */
object Secrets {
    private const val TOKEN_BYTES = 32
    private const val CODE_BOUND = 1_000_000
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun newCode(): String = "%06d".format(random.nextInt(CODE_BOUND))

    fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    fun codeHash(
        grantId: SignInGrantId,
        code: String,
    ): ByteArray = sha256(grantId.value.toString() + code)
}
