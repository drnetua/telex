package telex.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CatalogModelTest {
    private fun model(
        takes: Set<Modality>,
        produces: Set<Modality>,
    ) = CatalogModel(ModelId("a/b"), "B", "a", takes, produces, null, null, null, null)

    @Test
    fun `text slot is takes text and produces text`() {
        val m = model(setOf(Modality.TEXT), setOf(Modality.TEXT))
        assertThat(m.fits(ModelSlotKind.TEXT)).isTrue()
        assertThat(m.fits(ModelSlotKind.VISION)).isFalse()
        assertThat(m.fits(ModelSlotKind.IMAGE)).isFalse()
        assertThat(m.slots).containsExactly(ModelSlotKind.TEXT)
    }

    @Test
    fun `vision slot is takes images and produces text`() {
        val m = model(setOf(Modality.TEXT, Modality.IMAGE), setOf(Modality.TEXT))
        assertThat(m.slots).containsExactlyInAnyOrder(ModelSlotKind.TEXT, ModelSlotKind.VISION)
    }

    @Test
    fun `image-only input without text output does not fit vision`() {
        val m = model(setOf(Modality.IMAGE), setOf(Modality.IMAGE))
        assertThat(m.fits(ModelSlotKind.VISION)).isFalse()
        assertThat(m.slots).containsExactly(ModelSlotKind.IMAGE)
    }

    @Test
    fun `image slot is produces images`() {
        val m = model(setOf(Modality.TEXT), setOf(Modality.TEXT, Modality.IMAGE))
        assertThat(m.slots).containsExactlyInAnyOrder(ModelSlotKind.TEXT, ModelSlotKind.IMAGE)
    }

    @Test
    fun `a model that takes text but produces nothing usable fits no slot`() {
        assertThat(model(setOf(Modality.TEXT), emptySet()).slots).isEmpty()
        assertThat(BigDecimal.ONE).isNotNull()
    }
}
