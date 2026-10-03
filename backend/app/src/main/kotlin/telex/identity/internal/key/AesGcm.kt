package telex.identity.internal.key

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM sealing: the sealed form is a random 12-byte nonce, the ciphertext, then the 16-byte tag. */
object AesGcm {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    /** Throws [GeneralSecurityException] for a wrong key, wrong [aad], tampering or a truncated blob. */
    fun open(
        key: ByteArray,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        if (sealed.size < NONCE_BYTES + TAG_BYTES) throw GeneralSecurityException("sealed value is too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, NONCE_BYTES)),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }
}
