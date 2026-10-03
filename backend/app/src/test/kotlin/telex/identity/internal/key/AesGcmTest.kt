package telex.identity.internal.key

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class AesGcmTest {
    private val key = ByteArray(32) { it.toByte() }
    private val aad = "account-1".toByteArray()

    @Test
    fun `seal then open returns the plaintext and the sealed form is nonce plus ciphertext plus tag`() {
        val plain = ByteArray(32) { (it * 3).toByte() }
        val sealed = AesGcm.seal(key, plain, aad)
        assertThat(sealed).hasSize(12 + 32 + 16)
        assertThat(AesGcm.open(key, sealed, aad)).isEqualTo(plain)
    }

    @Test
    fun `a fresh nonce makes two seals of the same plaintext differ`() {
        val plain = ByteArray(32)
        assertThat(AesGcm.seal(key, plain, aad)).isNotEqualTo(AesGcm.seal(key, plain, aad))
    }

    @Test
    fun `open with another aad fails`() {
        val sealed = AesGcm.seal(key, ByteArray(32), aad)
        assertThatThrownBy { AesGcm.open(key, sealed, "account-2".toByteArray()) }
            .isInstanceOf(GeneralSecurityException::class.java)
    }

    @Test
    fun `open with another key fails`() {
        val sealed = AesGcm.seal(key, ByteArray(32), aad)
        assertThatThrownBy { AesGcm.open(ByteArray(32) { 9 }, sealed, aad) }
            .isInstanceOf(GeneralSecurityException::class.java)
    }

    @Test
    fun `a tampered byte fails`() {
        val sealed = AesGcm.seal(key, ByteArray(32), aad)
        sealed[20] = (sealed[20].toInt() xor 1).toByte()
        assertThatThrownBy { AesGcm.open(key, sealed, aad) }
            .isInstanceOf(GeneralSecurityException::class.java)
    }

    @Test
    fun `a truncated blob fails`() {
        assertThatThrownBy { AesGcm.open(key, ByteArray(10), aad) }
            .isInstanceOf(GeneralSecurityException::class.java)
    }
}
