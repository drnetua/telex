package telex.agents

import java.math.BigDecimal
import java.time.Instant

enum class CatalogViewState { CURRENT, UPDATE_FAILED, NOT_LOADED, NOT_CONFIGURED }

data class CatalogModelView(
    val modelId: String,
    val name: String,
    val provider: String,
    val takes: Set<String>,
    val produces: Set<String>,
    val slots: Set<ModelSlotKind>,
    val inputPerMtok: BigDecimal?,
    val outputPerMtok: BigDecimal?,
    val perImage: BigDecimal?,
    val contextLength: Int?,
)

/** The catalog for the Models page; `models` ordered by name, only models that fit at least one slot. */
data class ModelCatalogView(
    val state: CatalogViewState,
    val lastRefreshedAt: Instant?,
    val lastFailedAt: Instant?,
    val models: List<CatalogModelView>,
)
