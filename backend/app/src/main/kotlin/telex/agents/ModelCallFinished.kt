package telex.agents

import telex.identity.OwnerId
import telex.llm.ModelId

enum class ModelCallOutcome { ANSWERED, NO_MODEL_AVAILABLE, NO_MODEL_ANSWERED, AI_NOT_CONFIGURED }

data class ModelCallFinished(
    val callId: ModelCallId,
    val ownerId: OwnerId,
    val profile: ProfileRef,
    val slot: ModelSlotKind,
    val outcome: ModelCallOutcome,
    val answeredByModelId: ModelId?,
    val fallback: Boolean,
)
