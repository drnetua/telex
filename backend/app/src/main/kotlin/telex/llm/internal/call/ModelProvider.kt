package telex.llm.internal.call

import telex.llm.AttemptOutcome
import telex.llm.ModelId
import telex.llm.ModelSlotKind
import telex.llm.SlotAnswer
import telex.llm.SlotRequest

sealed interface ProviderResult {
    data class Answer(
        val answer: SlotAnswer,
    ) : ProviderResult

    data class Failure(
        val outcome: AttemptOutcome,
    ) : ProviderResult
}

/** Port T6 implements: one attempt for one model. */
interface ModelProvider {
    fun isConfigured(): Boolean

    fun attempt(
        modelId: ModelId,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProviderResult
}
