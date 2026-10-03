package telex.agents.internal.profile

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import telex.agents.ModelSlotKind
import telex.llm.ModelCatalog
import telex.llm.ModelCatalogRefreshed
import telex.llm.ModelId

/** Logs one WARN per bad system-profile model (profile, slot, model); a pure re-validation, log only. */
@Component
class OverrideValidator(
    private val profiles: SystemProfiles,
    private val catalog: ModelCatalog,
) {
    private val log = LoggerFactory.getLogger(OverrideValidator::class.java)

    @EventListener(ApplicationReadyEvent::class)
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

    private fun validate() {
        for (profile in profiles.all()) {
            for ((slot, chain) in profile.slots) {
                for (id in chain) {
                    val model = catalog.find(id)
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
