package telex.llm.internal.catalog

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.ModelCatalog
import telex.llm.ModelId
import java.util.concurrent.atomic.AtomicReference

/** Holds the catalog in memory; readers see one whole snapshot, swapped by a single reference. */
@Component
class CatalogHolder(
    private val store: CatalogSnapshotStore,
) : ModelCatalog {
    private val current = AtomicReference<CatalogSnapshot?>()

    /** Reload the in-memory snapshot from the store (also called on ApplicationReadyEvent). */
    @EventListener(ApplicationReadyEvent::class)
    fun load() {
        current.set(store.load())
    }

    override fun snapshot(): CatalogSnapshot = current.get() ?: store.load().also { current.compareAndSet(null, it) }

    override fun find(modelId: ModelId): CatalogModel? = snapshot().models[modelId]
}
