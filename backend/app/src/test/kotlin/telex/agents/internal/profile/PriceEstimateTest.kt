package telex.agents.internal.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.agents.ModelSlotKind
import telex.llm.CatalogModel
import telex.llm.Modality
import telex.llm.ModelId
import java.math.BigDecimal

class PriceEstimateTest {
    private val text = setOf(Modality.TEXT)

    private fun cm(
        id: String,
        input: String?,
        output: String?,
    ) = CatalogModel(ModelId(id), id, "p", text, text, input?.let(::BigDecimal), output?.let(::BigDecimal), null, null)

    @Test
    fun `worked check 0_15 in and 0_60 out gives 0_075 rounded half-up to 0_08`() {
        val e = PriceEstimate.of(cm("a", "0.15", "0.60"))
        assertThat(e.state).isEqualTo(PriceState.ESTIMATE)
        assertThat(e.amount).isEqualByComparingTo("0.08")
        assertThat(e.amount!!.scale()).isEqualTo(2)
    }

    @Test
    fun `a raw value above one cent is an estimate of 0_04`() {
        // 100 * (3000 * 0.10 + 500 * 0.20) / 1e6 = 0.04
        val e = PriceEstimate.of(cm("a", "0.10", "0.20"))
        assertThat(e.state).isEqualTo(PriceState.ESTIMATE)
        assertThat(e.amount).isEqualByComparingTo("0.04")
    }

    @Test
    fun `raw 0_004 is under-one-cent with no amount`() {
        // 100 * (3000 * 0.01 + 500 * 0.02) / 1e6 = 0.004
        val e = PriceEstimate.of(cm("a", "0.01", "0.02"))
        assertThat(e.state).isEqualTo(PriceState.UNDER_ONE_CENT)
        assertThat(e.amount).isNull()
    }

    @Test
    fun `raw value just below one cent compares unrounded - 0_0095 is under-one-cent`() {
        // 100 * (3000 * 0.03 + 500 * 0.01) / 1e6 = 0.0095 ; rounding would give 0.01
        val e = PriceEstimate.of(cm("a", "0.03", "0.01"))
        assertThat(e.state).isEqualTo(PriceState.UNDER_ONE_CENT)
        assertThat(e.amount).isNull()
    }

    @Test
    fun `raw exactly 0_01 is an estimate of 0_01`() {
        // 100 * (3000 * 0.02 + 500 * 0.08) / 1e6 = 0.01
        val e = PriceEstimate.of(cm("a", "0.02", "0.08"))
        assertThat(e.state).isEqualTo(PriceState.ESTIMATE)
        assertThat(e.amount).isEqualByComparingTo("0.01")
    }

    @Test
    fun `both prices zero is free`() {
        val e = PriceEstimate.of(cm("a", "0", "0"))
        assertThat(e.state).isEqualTo(PriceState.FREE)
        assertThat(e.amount).isNull()
    }

    @Test
    fun `AC-210 a missing price is unknown - either side`() {
        assertThat(PriceEstimate.of(cm("a", "1", null)).state).isEqualTo(PriceState.UNKNOWN)
        assertThat(PriceEstimate.of(cm("a", null, "1")).state).isEqualTo(PriceState.UNKNOWN)
        assertThat(PriceEstimate.of(cm("a", null, null)).state).isEqualTo(PriceState.UNKNOWN)
        assertThat(PriceEstimate.of(cm("a", "1", null)).amount).isNull()
    }

    @Test
    fun `no text model gives no-text-model with no amount`() {
        val e = PriceEstimate.of(null)
        assertThat(e.state).isEqualTo(PriceState.NO_TEXT_MODEL)
        assertThat(e.amount).isNull()
    }

    @Test
    fun `AC-10 the estimate follows the current model - fallback to B changes the price`() {
        val a = cm("a", "0.15", "0.60")
        val b = cm("b", "3", "15")
        val catalog = listOf(a, b).associateBy { it.modelId }
        val chain = listOf(ModelId("a"), ModelId("b"))

        fun price(c: Map<ModelId, CatalogModel>): PriceEstimate {
            val v = SlotResolution.resolve(ModelSlotKind.TEXT, chain, c)
            return PriceEstimate.of(v.currentModelId?.let { c[it] })
        }
        assertThat(price(catalog).amount).isEqualByComparingTo("0.08")
        // B: 100 * (3000*3 + 500*15) / 1e6 = 1.65
        assertThat(price(catalog - ModelId("a")).amount).isEqualByComparingTo("1.65")
        assertThat(price(catalog - ModelId("a") - ModelId("b")).state).isEqualTo(PriceState.NO_TEXT_MODEL)
    }
}
