package telex.agents.internal.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.agents.ModelSlotKind
import telex.llm.CatalogModel
import telex.llm.Modality
import telex.llm.ModelId
import java.math.BigDecimal

class SlotResolutionTest {
    private val text = setOf(Modality.TEXT)
    private val both = setOf(Modality.TEXT, Modality.IMAGE)

    private fun cm(
        id: String,
        takes: Set<Modality> = text,
        produces: Set<Modality> = text,
        input: String? = "1",
        output: String? = "1",
    ) = CatalogModel(
        ModelId(id),
        id,
        "p",
        takes,
        produces,
        input?.let(::BigDecimal),
        output?.let(::BigDecimal),
        null,
        null,
    )

    private fun catalog(vararg m: CatalogModel) = m.associateBy { it.modelId }

    private fun ids(vararg s: String) = s.map { ModelId(it) }

    private val a = cm("a")
    private val b = cm("b")

    @Test
    fun `main model available gives main-model with no warning`() {
        val v = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a", "b"), catalog(a, b))
        assertThat(v.state).isEqualTo(SlotState.MAIN_MODEL)
        assertThat(v.currentModelId).isEqualTo(ModelId("a"))
        assertThat(v.chain).containsExactly(
            ChainEntry(ModelId("a"), Availability.AVAILABLE),
            ChainEntry(ModelId("b"), Availability.AVAILABLE),
        )
    }

    @Test
    fun `AC-10 main model gone gives fallback on B and A returning restores main-model`() {
        val gone = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a", "b"), catalog(b))
        assertThat(gone.state).isEqualTo(SlotState.FALLBACK)
        assertThat(gone.currentModelId).isEqualTo(ModelId("b"))
        assertThat(gone.chain.map { it.availability })
            .containsExactly(Availability.NOT_IN_CATALOG, Availability.AVAILABLE)

        val back = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a", "b"), catalog(a, b))
        assertThat(back.state).isEqualTo(SlotState.MAIN_MODEL)
        assertThat(back.currentModelId).isEqualTo(ModelId("a"))
    }

    @Test
    fun `fallback skips an unavailable second model and uses the third`() {
        val v = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a", "b", "c"), catalog(cm("c")))
        assertThat(v.state).isEqualTo(SlotState.FALLBACK)
        assertThat(v.currentModelId).isEqualTo(ModelId("c"))
    }

    @Test
    fun `AC-223 no model of the chain in the catalog gives no-model-available`() {
        val v = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a", "b"), catalog())
        assertThat(v.state).isEqualTo(SlotState.NO_MODEL_AVAILABLE)
        assertThat(v.currentModelId).isNull()
        assertThat(v.chain.map { it.availability }).containsOnly(Availability.NOT_IN_CATALOG)
    }

    @Test
    fun `AC-223 empty vision and image slots are not-used`() {
        val c = catalog(a)
        for (slot in listOf(ModelSlotKind.VISION, ModelSlotKind.IMAGE)) {
            val v = SlotResolution.resolve(slot, emptyList(), c)
            assertThat(v.state).isEqualTo(SlotState.NOT_USED)
            assertThat(v.currentModelId).isNull()
        }
    }

    @Test
    fun `AC-223 a model of another slot is never used - not-capable is skipped`() {
        // t-only model in the vision slot: in catalog but cannot do vision
        val v = SlotResolution.resolve(ModelSlotKind.VISION, ids("a"), catalog(a))
        assertThat(v.state).isEqualTo(SlotState.NO_MODEL_AVAILABLE)
        assertThat(v.currentModelId).isNull()
        assertThat(v.chain.single().availability).isEqualTo(Availability.NOT_CAPABLE)

        val vision = cm("v", takes = both)
        val v2 = SlotResolution.resolve(ModelSlotKind.VISION, ids("a", "v"), catalog(a, vision))
        assertThat(v2.state).isEqualTo(SlotState.FALLBACK)
        assertThat(v2.currentModelId).isEqualTo(ModelId("v"))
    }

    @Test
    fun `empty catalog (not-loaded or not-configured) makes every non-empty slot no-model-available`() {
        for (slot in ModelSlotKind.entries) {
            val v = SlotResolution.resolve(slot, ids("a"), emptyMap())
            assertThat(v.state).isEqualTo(SlotState.NO_MODEL_AVAILABLE)
        }
    }

    @Test
    fun `choosable is false only when the text slot has no available model`() {
        val ok = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a"), catalog(a))
        val none = SlotResolution.resolve(ModelSlotKind.TEXT, ids("a"), catalog())
        assertThat(SlotResolution.choosable(ok)).isTrue()
        assertThat(SlotResolution.choosable(none)).isFalse()
    }
}
