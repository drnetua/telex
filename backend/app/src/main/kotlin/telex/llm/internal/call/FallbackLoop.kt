package telex.llm.internal.call

import telex.llm.Attempt
import telex.llm.AttemptOutcome
import telex.llm.ModelCallFailure
import telex.llm.ModelCallResult
import telex.llm.ModelCalls
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.llm.ModelSlotKind
import telex.llm.SlotRequest
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class FallbackLoop(
    private val provider: ModelProvider,
    private val catalog: ModelCatalog,
    private val attemptTimeout: Duration,
) : ModelCalls {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    override fun call(
        chain: List<ModelId>,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ModelCallResult {
        if (!provider.isConfigured()) return ModelCallResult.Failed(ModelCallFailure.AI_NOT_CONFIGURED, emptyList())
        val attempts = mutableListOf<Attempt>()
        var answered: ModelCallResult.Answered? = null
        for (modelId in chain) {
            val result = attemptOne(modelId, slot, request)
            attempts += Attempt(modelId, result.outcome)
            if (result is ProviderResult.Answer) {
                answered = ModelCallResult.Answered(result.answer, modelId, modelId != chain.first(), attempts)
            }
            if (answered != null || !result.outcome.movesOn || Thread.currentThread().isInterrupted) break
        }
        return answered ?: ModelCallResult.Failed(ModelCallFailure.NO_MODEL_ANSWERED, attempts)
    }

    private val ProviderResult.outcome: AttemptOutcome
        get() = if (this is ProviderResult.Failure) outcome else AttemptOutcome.ANSWERED

    private fun attemptOne(
        modelId: ModelId,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProviderResult {
        val model = catalog.find(modelId)
        if (model == null || !model.fits(slot)) return ProviderResult.Failure(AttemptOutcome.MISSING)
        val future = executor.submit(Callable { provider.attempt(modelId, slot, request) })
        return try {
            future.get(attemptTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            ProviderResult.Failure(AttemptOutcome.TIMEOUT)
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            ProviderResult.Failure(AttemptOutcome.TIMEOUT)
        } catch (_: ExecutionException) {
            ProviderResult.Failure(AttemptOutcome.PROVIDER_ERROR)
        }
    }
}
