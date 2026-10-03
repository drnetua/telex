package telex.agents

import java.math.BigDecimal

enum class SlotViewState { MAIN_MODEL, FALLBACK, NO_MODEL_AVAILABLE, NOT_USED }

enum class AvailabilityView { AVAILABLE, NOT_IN_CATALOG, NOT_CAPABLE }

enum class PriceViewState { ESTIMATE, UNDER_ONE_CENT, FREE, UNKNOWN, NO_TEXT_MODEL }

data class ChainModelView(
    val modelId: String,
    val name: String?,
    val availability: AvailabilityView,
)

data class SlotView(
    val state: SlotViewState,
    val currentModelId: String?,
    val chain: List<ChainModelView>,
)

data class PriceView(
    val state: PriceViewState,
    val amount: BigDecimal?,
)

data class ModelProfileView(
    val ref: ProfileRef,
    val name: String,
    val slots: Map<ModelSlotKind, SlotView>,
    val price: PriceView,
    val choosable: Boolean,
)

data class ModelProfileListView(
    val aiConfigured: Boolean,
    val defaultProfile: ProfileRef,
    val customProfileLimit: Int,
    val items: List<ModelProfileView>,
)

data class DraftView(
    val name: String,
    val duplicatedFrom: ProfileRef?,
    val slots: Map<ModelSlotKind, SlotView>,
    val price: PriceView,
)
