package telex.agents

import telex.identity.OwnerId
import telex.llm.Attempt
import telex.llm.ModelId
import telex.llm.SlotAnswer
import telex.llm.SlotRequest
import java.util.UUID

@JvmInline
value class ModelCallId(
    val value: UUID,
)

enum class SlotFailure { NO_MODEL_AVAILABLE, NO_MODEL_ANSWERED, AI_NOT_CONFIGURED, PROFILE_NOT_FOUND }

sealed interface ProfileCallResult {
    val callId: ModelCallId
    val attempts: List<Attempt>

    data class Answered(
        override val callId: ModelCallId,
        val answer: SlotAnswer,
        val answeredBy: ModelId,
        val fallback: Boolean,
        override val attempts: List<Attempt>,
    ) : ProfileCallResult

    data class Failed(
        override val callId: ModelCallId,
        val reason: SlotFailure,
        override val attempts: List<Attempt>,
    ) : ProfileCallResult
}

interface ProfileCalls {
    fun call(
        ownerId: OwnerId,
        profile: ProfileRef,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProfileCallResult
}
