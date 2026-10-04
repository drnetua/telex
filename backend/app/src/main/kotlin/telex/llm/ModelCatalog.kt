package telex.llm

import java.time.Instant

enum class CatalogState { CURRENT, UPDATE_FAILED, NOT_LOADED, NOT_CONFIGURED }

data class CatalogSnapshot(
    val models: Map<ModelId, CatalogModel>,
    val refreshedAt: Instant?,
    val failedAt: Instant?,
    val state: CatalogState,
)

/** The current Model Catalog snapshot, served from memory. */
interface ModelCatalog {
    fun snapshot(): CatalogSnapshot

    fun find(modelId: ModelId): CatalogModel?
}
