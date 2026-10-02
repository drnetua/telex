package telex.identity.internal.owner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailAddressTest {
    @Test
    fun `plus tag and letter case are ignored in the canonical form, the typed form is kept`() {
        val email = EmailAddress.parse("Anton+work@Mail.com")

        assertThat(email).isNotNull
        assertThat(email!!.canonical).isEqualTo("anton@mail.com")
        assertThat(email.asTyped).isEqualTo("Anton+work@Mail.com")
    }

    @Test
    fun `an empty plus tag is dropped`() {
        assertThat(EmailAddress.parse("anton+@mail.com")!!.canonical).isEqualTo("anton@mail.com")
    }

    @Test
    fun `addresses differing by case and tag share one canonical form`() {
        assertThat(EmailAddress.parse("Anton+work@Mail.com")!!.canonical)
            .isEqualTo(EmailAddress.parse("anton@mail.com")!!.canonical)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["me@localhost", "@example.com", "me@", "a@b@c.com", "me @x.com", "", "me", "a,b@c.de", "x(y@z.co"],
    )
    fun `incomplete addresses are refused`(raw: String) {
        assertThat(EmailAddress.parse(raw)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Name<a@b.com>", "\"Name\"<a@b.com>", "<a@b.com>", "group:a@b.com;", "a@b.com<c@d.com>"])
    fun `a display name or group form is not a plain mailbox`(raw: String) {
        assertThat(EmailAddress.parse(raw)).isNull()
    }

    @Test
    fun `an address longer than 254 characters is refused`() {
        val tooLong = "a".repeat(250) + "@b.co"
        assertThat(tooLong.length).isGreaterThan(254)
        assertThat(EmailAddress.parse(tooLong)).isNull()
        val maxLen = "a".repeat(254 - "@b.co".length) + "@b.co"
        assertThat(EmailAddress.parse(maxLen)).isNotNull
    }
}
