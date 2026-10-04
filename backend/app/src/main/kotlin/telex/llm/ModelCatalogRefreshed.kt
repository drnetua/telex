package telex.llm

import java.time.Instant

/** Published when a refresh replaced the catalog snapshot (not on a failed refresh or without a key). */
data class ModelCatalogRefreshed(
    val refreshedAt: Instant,
    val modelCount: Int,
)
