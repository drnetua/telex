package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicUrlTest {
    @Test
    fun `https url is secure and gives host and origin`() {
        val url = PublicUrl("https://telex.example.test")

        assertThat(url.isSecure).isTrue()
        assertThat(url.host).isEqualTo("telex.example.test")
        assertThat(url.origin).isEqualTo("https://telex.example.test")
    }

    @Test
    fun `http localhost default is not secure and keeps the port in the origin only`() {
        val url = PublicUrl("http://localhost:8080")

        assertThat(url.isSecure).isFalse()
        assertThat(url.host).isEqualTo("localhost")
        assertThat(url.origin).isEqualTo("http://localhost:8080")
    }

    @Test
    fun `a trailing slash or path is normalised away`() {
        assertThat(PublicUrl("https://telex.example.test/").origin).isEqualTo("https://telex.example.test")
        assertThat(PublicUrl("https://telex.example.test:8443/app/x").origin)
            .isEqualTo("https://telex.example.test:8443")
    }

    @Test
    fun `links never double the slash and may carry a fragment`() {
        val url = PublicUrl("https://telex.example.test/")

        assertThat(url.link("/signin/link")).isEqualTo("https://telex.example.test/signin/link")
        assertThat(url.link("signin/link", "tok")).isEqualTo("https://telex.example.test/signin/link#tok")
    }
}
