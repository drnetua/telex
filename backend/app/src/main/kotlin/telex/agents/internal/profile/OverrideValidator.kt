package telex.agents.internal.profile

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import telex.agents.ModelSlotKind
import telex.llm.ModelCatalog
import telex.llm.ModelCatalogRefreshed
import telex.llm.ModelId
import java.time.Instant

/** Logs one WARN per bad system-profile model (profile, slot, model); a pure re-validation, log only. */
@Component
class OverrideValidator(
    private val profiles: SystemProfiles,
    private val catalog: ModelCatalog,
) {
    private val log = LoggerFactory.getLogger(OverrideValidator::class.java)

    @Volatile
    private var validatedAt: Instant? = null

    /** After the catalog refresher (order 1), so startup sees the fresh snapshot and the refresh event adds nothing. */
    @EventListener(ApplicationReadyEvent::class)
    @Order(2)
    fun onReady() {
        profiles.unknownEntries.forEach { log.warn("Ignoring unknown system profile setting: {}", it) }
        validate()
    }

    @ApplicationModuleListener
    fun onCatalogRefreshed(
        @Suppress("UNUSED_PARAMETER") event: ModelCatalogRefreshed,
    ) {
        validate()
    }

    /** One pass per catalog snapshot: the startup pass and the refresh event for the same refresh log once. */
    @Synchronized
    private fun validate() {
        val snapshot = catalog.snapshot()
        val at = snapshot.refreshedAt
        if (at != null && at == validatedAt) return
        validatedAt = at
        for (profile in profiles.all()) {
            for ((slot, chain) in profile.slots) {
                for (id in chain) {
                    val model = snapshot.models[id]
                    when {
                        model == null -> warn(profile, slot, id, "is not in the model catalog")
                        !model.fits(slot.toLlm()) -> warn(profile, slot, id, "cannot do this slot's job")
                    }
                }
                profile.ignored[slot].orEmpty().forEach { warn(profile, slot, it, "is beyond the third and ignored") }
            }
        }
    }

    private fun warn(
        profile: SystemProfile,
        slot: ModelSlotKind,
        id: ModelId,
        problem: String,
    ) = log.warn("System profile '{}' slot '{}': model '{}' {}", profile.key.wire, slot.wire, id.value, problem)
}
