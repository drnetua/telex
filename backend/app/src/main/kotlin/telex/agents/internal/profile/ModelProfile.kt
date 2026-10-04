package telex.agents.internal.profile

import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.llm.ModelId

/** An Owner's custom Model Profile: trimmed name plus three ordered chains (index 0 is the main model). */
data class ModelProfile(
    val id: ModelProfileId,
    val name: String,
    val slots: Map<ModelSlotKind, List<ModelId>>,
) {
    fun toDraft(): ProfileDraft = ProfileDraft(name, slots)
}
