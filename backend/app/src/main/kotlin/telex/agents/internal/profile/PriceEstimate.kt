package telex.agents.internal.profile

import telex.llm.CatalogModel
import java.math.BigDecimal
import java.math.RoundingMode

enum class PriceState { ESTIMATE, UNDER_ONE_CENT, FREE, UNKNOWN, NO_TEXT_MODEL }

data class PriceEstimate(
    val state: PriceState,
    val amount: BigDecimal?,
) {
    companion object {
        private val INPUT_TOKENS = BigDecimal(3000)
        private val OUTPUT_TOKENS = BigDecimal(500)
        private val RUNS = BigDecimal(100)
        private val MILLION = BigDecimal(1_000_000)
        private val ONE_CENT = BigDecimal("0.01")

        /** [textModel] is the text slot's current catalog model, or null when none is available. */
        fun of(textModel: CatalogModel?): PriceEstimate {
            val input = textModel?.inputPerMtok
            val output = textModel?.outputPerMtok
            return when {
                textModel == null -> PriceEstimate(PriceState.NO_TEXT_MODEL, null)
                input == null || output == null -> PriceEstimate(PriceState.UNKNOWN, null)
                input.signum() == 0 && output.signum() == 0 -> PriceEstimate(PriceState.FREE, null)
                else -> estimate(input, output)
            }
        }

        private fun estimate(
            input: BigDecimal,
            output: BigDecimal,
        ): PriceEstimate {
            val raw = RUNS.multiply(INPUT_TOKENS * input + OUTPUT_TOKENS * output).divide(MILLION)
            return if (raw < ONE_CENT) {
                PriceEstimate(PriceState.UNDER_ONE_CENT, null)
            } else {
                PriceEstimate(PriceState.ESTIMATE, raw.setScale(2, RoundingMode.HALF_UP))
            }
        }
    }
}
