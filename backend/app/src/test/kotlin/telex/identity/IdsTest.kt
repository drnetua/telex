package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.shared.TypedId
import telex.shared.Uuid7

class IdsTest {
    @Test
    fun `identity ids are typed ids over a uuid`() {
        val uuid = Uuid7.next()

        assertThat(OwnerId(uuid)).isInstanceOf(TypedId::class.java)
        assertThat(OwnerId(uuid).value).isEqualTo(uuid)
        assertThat(SignInGrantId(uuid).value).isEqualTo(uuid)
        assertThat(SignInSessionId(uuid).value).isEqualTo(uuid)
    }
}
