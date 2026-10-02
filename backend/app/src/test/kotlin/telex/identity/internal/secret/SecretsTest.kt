package telex.identity.internal.secret

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.identity.SignInGrantId
import telex.shared.Uuid7
import java.util.Base64

class SecretsTest {
    @Test
    fun `a token is 256 bits of Base64URL and never repeats`() {
        val token = Secrets.newToken()

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32)
        assertThat(token).doesNotContain("=", "+", "/")
        assertThat(Secrets.newToken()).isNotEqualTo(token)
    }

    @Test
    fun `a code is six digits`() {
        repeat(200) { assertThat(Secrets.newCode()).matches("\\d{6}") }
    }

    @Test
    fun `sha256 of a token is a 32 byte digest, deterministic`() {
        assertThat(Secrets.sha256("abc")).hasSize(32)
        assertThat(Secrets.sha256("abc")).isEqualTo(Secrets.sha256("abc"))
        assertThat(Secrets.sha256("abc")).isNotEqualTo(Secrets.sha256("abd"))
        assertThat(Secrets.sha256("abc").joinToString("") { "%02x".format(it) })
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `a code hash is bound to the grant id`() {
        val a = SignInGrantId(Uuid7.next())
        val b = SignInGrantId(Uuid7.next())

        assertThat(Secrets.codeHash(a, "123456")).hasSize(32)
        assertThat(Secrets.codeHash(a, "123456")).isEqualTo(Secrets.codeHash(a, "123456"))
        assertThat(Secrets.codeHash(a, "123456")).isNotEqualTo(Secrets.codeHash(b, "123456"))
        assertThat(Secrets.codeHash(a, "123456")).isNotEqualTo(Secrets.codeHash(a, "654321"))
        assertThat(Secrets.codeHash(a, "123456")).isEqualTo(Secrets.sha256(a.value.toString() + "123456"))
    }
}
