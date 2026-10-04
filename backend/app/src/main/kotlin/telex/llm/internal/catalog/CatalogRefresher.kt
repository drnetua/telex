package telex.llm.internal.catalog

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import telex.llm.ModelCatalogRefreshed
import telex.llm.internal.openrouter.LlmProperties
import telex.llm.internal.openrouter.ModelListParser
import telex.llm.internal.openrouter.OpenRouterModelsClient
import telex.llm.internal.openrouter.OpenRouterProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Keeps the catalog fresh: at start, every refresh-interval, and every retry-interval after a failure. */
@Component
@Suppress("LongParameterList")
class CatalogRefresher(
    private val client: OpenRouterModelsClient,
    private val store: CatalogSnapshotStore,
    private val holder: CatalogHolder,
    private val openRouter: OpenRouterProperties,
    private val llm: LlmProperties,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
    private val meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger("telex.llm.catalog")

    @Volatile
    private var next: Instant? = null

    val nextRunAt: Instant? get() = next

    init {
        Gauge
            .builder("telex.llm.catalog.age.hours") { ageHours() }
            .description("Hours since the last successful catalog refresh (NaN before the first)")
            .register(meters)
        Gauge
            .builder("telex.llm.catalog.models") {
                holder
                    .snapshot()
                    .models.size
                    .toDouble()
            }.description("Models in the current catalog snapshot")
            .register(meters)
    }

    private fun ageHours(): Double =
        holder.snapshot().refreshedAt?.let { Duration.between(it, clock.instant()).toMinutes() / MINUTES_PER_HOUR }
            ?: Double.NaN

    /** Startup: refresh now (or WARN naming TELEX_OPENROUTER_API_KEY and stay not-configured). */
    @EventListener(ApplicationReadyEvent::class)
    @Order(1) // after CatalogHolder.load()
    fun start() {
        if (!openRouter.configured) {
            log.warn("No model provider key configured: set TELEX_OPENROUTER_API_KEY to enable AI models")
            holder.markNotConfigured()
            return
        }
        refresh()
    }

    /** Scheduler tick: refresh only if clock.instant() >= nextRunAt. */
    @Scheduled(fixedDelay = TICK_MS, initialDelay = TICK_MS)
    fun refreshIfDue() {
        val due = next ?: return
        if (clock.instant() >= due) refresh()
    }

    private fun refresh() {
        val now = clock.instant()
        val outcome =
            try {
                val models = ModelListParser.parse(client.fetchModels()).models
                if (models.isEmpty()) {
                    error("provider returned no usable models")
                }
                store.replace(models, now) {
                    // the holder must hold the new snapshot before the event can be consumed; the read joins this
                    // transaction, and the load after the try restores the committed state if it rolls back
                    holder.load()
                    events.publishEvent(ModelCatalogRefreshed(now, models.size))
                }
                log.info("Model catalog refreshed: {} models", models.size)
                true
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // class name only: exception messages may carry request details
                log.warn("Model catalog refresh failed ({}); keeping the last snapshot", e.javaClass.simpleName)
                runCatching { store.recordFailure(now) }
                false
            }
        holder.load()
        meters.counter("telex.llm.catalog.refresh", "outcome", if (outcome) "ok" else "failed").increment()
        val wait: Duration = if (outcome) llm.catalog.refreshInterval else llm.catalog.retryInterval
        next = now.plus(wait)
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val MINUTES_PER_HOUR = 60.0
    }
}
