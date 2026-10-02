package telex.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class IdsTest {
    @Test
    fun `ids are version 7 with the RFC 9562 variant`() {
        val id = Uuid7.next()

        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }

    @Test
    fun `ids carry the generation time`() {
        val before = System.currentTimeMillis()
        val id = Uuid7.next()
        val after = System.currentTimeMillis()

        assertThat(Uuid7.timestampMillis(id)).isBetween(before, after + 1)
    }

    @Test
    fun `ids are strictly ordered by generation, also within one millisecond`() {
        val ids = List(10_000) { Uuid7.next() }

        assertThat(ids).isSorted().doesNotHaveDuplicates()
        assertThat(ids.map { it.toString() }).isSorted()
    }

    @Test
    fun `typed ids wrap a UUID`() {
        val uuid = Uuid7.next()

        assertThat(SampleId(uuid).value).isEqualTo(uuid)
    }

    @JvmInline
    value class SampleId(
        override val value: UUID,
    ) : TypedId
}
