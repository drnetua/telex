package telex.agents.internal.profile

import telex.agents.ModelSlotKind
import telex.llm.CatalogModel
import telex.llm.ModelId
import telex.shared.FieldProblem

/** Turns a broken rule into the field problem the editor shows (message wording follows the openapi examples). */
object FieldProblems {
    fun of(
        error: FieldError,
        draft: ProfileDraft,
        models: Map<ModelId, CatalogModel>,
    ): FieldProblem {
        val field = error.field
        val model = modelAt(field, draft)?.let { models[it]?.name ?: it.value }
        val message =
            when (error.code) {
                "name-required" -> "Give the profile a name."

                "name-too-long" -> "The name can be at most ${ProfileRules.MAX_NAME} characters."

                "name-taken" -> "You already have a profile called ${draft.name.trim()}."

                "name-reserved" -> "${draft.name.trim()} is the name of a system profile. Pick another name."

                "text-slot-required" -> "The text slot needs at least one model."

                "model-not-capable" -> "$model can't be used in the ${field.substringAfter(
                    '.',
                ).substringBefore('[')} slot."

                "slot-full" -> "A slot holds at most ${ProfileRules.MAX_CHAIN} models."

                "model-duplicate" -> "$model is already in this slot."

                else -> "$model is no longer available. Pick another model."
            }
        return FieldProblem(field, error.code, message)
    }

    private fun modelAt(
        field: String,
        draft: ProfileDraft,
    ): ModelId? {
        val slot = ModelSlotKind.entries.firstOrNull { field.startsWith("slots.${it.wire}[") } ?: return null
        return draft.slots[slot]?.getOrNull(field.substringAfter('[').substringBefore(']').toInt())
    }
}
