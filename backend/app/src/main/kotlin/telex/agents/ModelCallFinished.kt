package telex.agents

import com.fasterxml.jackson.annotation.JsonValue
import telex.identity.OwnerId
import telex.llm.ModelId

/** Serialized by its kebab-case [wire] value (events.md); also stored in `model_call.outcome`. */
enum class ModelCallOutcome(
    @get:JsonValue val wire: String,
) {
    ANSWERED("answered"),
    NO_MODEL_AVAILABLE("no-model-available"),
    NO_MODEL_ANSWERED("no-model-answered"),
    AI_NOT_CONFIGURED("ai-not-configured"),
}

data class ModelCallFinished(
    val callId: ModelCallId,
    val ownerId: OwnerId,
    val profile: ProfileRef,
    val slot: ModelSlotKind,
    val outcome: ModelCallOutcome,
    val answeredByModelId: ModelId?,
    val fallback: Boolean,
)
