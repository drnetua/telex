package telex.agents.internal.profile

import telex.agents.ModelSlotKind
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.llm.CatalogModel
import telex.llm.ModelId
import telex.llm.ModelSlotKind as LlmSlotKind

/** One broken rule: `field` is `name`, `slots.<slot>` or `slots.<slot>[<index>]`. */
data class FieldError(
    val field: String,
    val code: String,
)

/** Name plus the three ordered fallback chains (index 0 is the main model). */
data class ProfileDraft(
    val name: String,
    val slots: Map<ModelSlotKind, List<ModelId>>,
)

object ProfileRules {
    const val MAX_NAME = 40
    const val MAX_CHAIN = 3
    const val MAX_CUSTOM_PROFILES = 20

    /**
     * @param ownNames names of the Owner's other custom profiles (not the one being edited)
     * @param baseline models already stored in the profile (or copied from the duplicated source) per slot;
     *  models in the baseline are exempt from the "must be in the catalog" rule
     */
    fun validate(
        draft: ProfileDraft,
        catalog: Map<ModelId, CatalogModel>,
        ownNames: Collection<String>,
        baseline: Map<ModelSlotKind, List<ModelId>> = emptyMap(),
    ): List<FieldError> =
        nameErrors(draft.name.trim(), ownNames) +
            ModelSlotKind.entries.flatMap { slotErrors(it, draft.slots[it].orEmpty(), catalog, baseline[it].orEmpty()) }

    private fun nameErrors(
        name: String,
        ownNames: Collection<String>,
    ): List<FieldError> {
        val code =
            when {
                name.isEmpty() -> "name-required"
                name.length > MAX_NAME -> "name-too-long"
                SystemProfileKey.entries.any { it.displayName.lowercase() == name.lowercase() } -> "name-reserved"
                ownNames.any { it.trim().lowercase() == name.lowercase() } -> "name-taken"
                else -> return emptyList()
            }
        return listOf(FieldError("name", code))
    }

    private fun slotErrors(
        slot: ModelSlotKind,
        models: List<ModelId>,
        catalog: Map<ModelId, CatalogModel>,
        baseline: List<ModelId>,
    ): List<FieldError> {
        val field = "slots.${slot.wire}"
        val errors = mutableListOf<FieldError>()
        if (slot == ModelSlotKind.TEXT && models.isEmpty()) errors += FieldError(field, "text-slot-required")
        val seen = mutableSetOf<ModelId>()
        models.forEachIndexed { i, id ->
            val at = "$field[$i]"
            val known = catalog[id]
            when {
                i >= MAX_CHAIN -> errors += FieldError(at, "slot-full")
                !seen.add(id) -> errors += FieldError(at, "model-duplicate")
                known == null -> if (id !in baseline) errors += FieldError(at, "model-left-catalog")
                !known.fits(slot.toLlm()) -> errors += FieldError(at, "model-not-capable")
            }
        }
        return errors
    }

    private fun ModelSlotKind.toLlm(): LlmSlotKind = LlmSlotKind.valueOf(name)

    fun canCreate(ownCustomProfileCount: Int): Boolean = ownCustomProfileCount < MAX_CUSTOM_PROFILES

    fun isEditable(ref: ProfileRef): Boolean = ref is ProfileRef.Custom

    fun copyName(
        sourceName: String,
        takenNames: Collection<String>,
    ): String {
        val taken = takenNames.mapTo(hashSetOf()) { it.trim().lowercase() }
        val base = "${sourceName.trim()} copy"
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base $it" }
            .first { it.lowercase() !in taken }
    }
}
