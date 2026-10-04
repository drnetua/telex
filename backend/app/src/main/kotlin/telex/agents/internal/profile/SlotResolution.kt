package telex.agents.internal.profile

import telex.agents.ModelSlotKind
import telex.llm.CatalogModel
import telex.llm.ModelId

enum class SlotState { MAIN_MODEL, FALLBACK, NO_MODEL_AVAILABLE, NOT_USED }

enum class Availability { AVAILABLE, NOT_IN_CATALOG, NOT_CAPABLE }

data class ChainEntry(
    val modelId: ModelId,
    val availability: Availability,
)

data class SlotView(
    val slot: ModelSlotKind,
    val state: SlotState,
    val currentModelId: ModelId?,
    val chain: List<ChainEntry>,
)

object SlotResolution {
    fun resolve(
        slot: ModelSlotKind,
        chain: List<ModelId>,
        catalog: Map<ModelId, CatalogModel>,
    ): SlotView {
        val llmSlot = slot.toLlm()
        val entries =
            chain.map { id ->
                val model = catalog[id]
                val availability =
                    when {
                        model == null -> Availability.NOT_IN_CATALOG
                        model.fits(llmSlot) -> Availability.AVAILABLE
                        else -> Availability.NOT_CAPABLE
                    }
                ChainEntry(id, availability)
            }
        val index = entries.indexOfFirst { it.availability == Availability.AVAILABLE }
        val state =
            when {
                chain.isEmpty() && slot != ModelSlotKind.TEXT -> SlotState.NOT_USED
                index == 0 -> SlotState.MAIN_MODEL
                index > 0 -> SlotState.FALLBACK
                else -> SlotState.NO_MODEL_AVAILABLE
            }
        return SlotView(slot, state, entries.getOrNull(index)?.modelId, entries)
    }

    /** A profile can be chosen as a new default only when its text slot has an available model. */
    fun choosable(text: SlotView): Boolean = text.currentModelId != null
}
