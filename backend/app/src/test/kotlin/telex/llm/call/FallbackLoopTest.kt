package telex.llm.call

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.llm.Attempt
import telex.llm.AttemptOutcome
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.ChatMessage
import telex.llm.Modality
import telex.llm.ModelCallFailure
import telex.llm.ModelCallResult
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.llm.ModelSlotKind
import telex.llm.SlotAnswer
import telex.llm.SlotRequest
import telex.llm.internal.call.FallbackLoop
import telex.llm.internal.call.ModelProvider
import telex.llm.internal.call.ProviderResult
import java.time.Duration
import java.time.Instant

class FallbackLoopTest {
    private val a = ModelId("x/a")
    private val b = ModelId("x/b")
    private val request = SlotRequest.Text(listOf(ChatMessage("user", "hi")))

    private fun textModel(id: ModelId) =
        CatalogModel(id, id.value, "x", setOf(Modality.TEXT), setOf(Modality.TEXT), null, null, null, null)

    private class FakeCatalog(
        models: List<CatalogModel>,
    ) : ModelCatalog {
        private val map = models.associateBy { it.modelId }

        override fun snapshot() = CatalogSnapshot(map, Instant.EPOCH, null, CatalogState.CURRENT)

        override fun find(modelId: ModelId) = map[modelId]
    }

    private class FakeProvider(
        private val configured: Boolean = true,
        private val script: Map<ModelId, () -> ProviderResult>,
    ) : ModelProvider {
        val calls = mutableListOf<Pair<ModelId, SlotRequest>>()

        override fun isConfigured() = configured

        override fun attempt(
            modelId: ModelId,
            slot: ModelSlotKind,
            request: SlotRequest,
        ): ProviderResult {
            synchronized(calls) { calls += modelId to request }
            return script.getValue(modelId)()
        }
    }

    private fun answer(text: String) = { ProviderResult.Answer(SlotAnswer.Text(text)) }

    private fun fail(outcome: AttemptOutcome) = { ProviderResult.Failure(outcome) }

    private fun loop(
        provider: ModelProvider,
        models: List<CatalogModel> = listOf(textModel(a), textModel(b)),
        timeout: Duration = Duration.ofMillis(200),
    ) = FallbackLoop(provider, FakeCatalog(models), timeout)

    @Test
    fun `first model answers with no fallback`() {
        val p = FakeProvider(script = mapOf(a to answer("A")))
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(a)
        assertThat(r.fallback).isFalse()
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.ANSWERED))
    }

    @Test
    fun `main model missing from catalog is skipped without a provider call and backup answers (AC-10)`() {
        val p = FakeProvider(script = mapOf(b to answer("B")))
        val r =
            loop(p, models = listOf(textModel(b)))
                .call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(r.answeredBy).isEqualTo(b)
        assertThat(r.fallback).isTrue()
        assertThat(r.attempts)
            .containsExactly(Attempt(a, AttemptOutcome.MISSING), Attempt(b, AttemptOutcome.ANSWERED))
        assertThat(p.calls.map { it.first }).containsExactly(b)
    }

    @Test
    fun `model that cannot do the slot is skipped as missing`() {
        val imageOnly =
            CatalogModel(a, "a", "x", setOf(Modality.TEXT), setOf(Modality.IMAGE), null, null, null, null)
        val p = FakeProvider(script = mapOf(b to answer("B")))
        val r =
            loop(p, models = listOf(imageOnly, textModel(b)))
                .call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(r.attempts.first()).isEqualTo(Attempt(a, AttemptOutcome.MISSING))
        assertThat(p.calls.map { it.first }).containsExactly(b)
    }

    @Test
    fun `every move-on outcome falls back to B with the same request (AC-224)`() {
        AttemptOutcome.entries.filter { it.movesOn && it != AttemptOutcome.MISSING }.forEach { outcome ->
            val p = FakeProvider(script = mapOf(a to fail(outcome), b to answer("B")))
            val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
            assertThat(r.answeredBy).`as`("$outcome").isEqualTo(b)
            assertThat(r.fallback).isTrue()
            assertThat(r.attempts).containsExactly(Attempt(a, outcome), Attempt(b, AttemptOutcome.ANSWERED))
            assertThat(p.calls.map { it.second }).containsOnly(request)
        }
    }

    @Test
    fun `a stalled model is recorded as timeout and the next model is tried (AC-224)`() {
        val p =
            FakeProvider(
                script =
                    mapOf(
                        a to {
                            Thread.sleep(5_000)
                            ProviderResult.Answer(SlotAnswer.Text("late"))
                        },
                        b to answer("B"),
                    ),
            )
        val started = System.nanoTime()
        val r =
            loop(p, timeout = Duration.ofMillis(100))
                .call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3))
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.TIMEOUT), Attempt(b, AttemptOutcome.ANSWERED))
    }

    @Test
    fun `the next call starts again from the first model (AC-224)`() {
        var first = true
        val p =
            FakeProvider(
                script =
                    mapOf(
                        a to { if (first) fail(AttemptOutcome.UNAVAILABLE)() else answer("A")() },
                        b to answer("B"),
                    ),
            )
        val l = loop(p)
        val one = l.call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        first = false
        val two = l.call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(one.answeredBy).isEqualTo(b)
        assertThat(two.answeredBy).isEqualTo(a)
        assertThat(two.fallback).isFalse()
    }

    @Test
    fun `content refusal stops at once and never calls the next model (AC-228)`() {
        val p = FakeProvider(script = mapOf(a to fail(AttemptOutcome.CONTENT_REFUSED), b to answer("B")))
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.CONTENT_REFUSED))
        assertThat(p.calls.map { it.first }).containsExactly(a)
    }

    @Test
    fun `invalid request stops at once (AC-228)`() {
        val p = FakeProvider(script = mapOf(a to fail(AttemptOutcome.INVALID_REQUEST), b to answer("B")))
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.INVALID_REQUEST))
        assertThat(p.calls.map { it.first }).containsExactly(a)
    }

    @Test
    fun `all models failing yields no-model-answered with every attempt (AC-228)`() {
        val p =
            FakeProvider(
                script = mapOf(a to fail(AttemptOutcome.RATE_LIMITED), b to fail(AttemptOutcome.PROVIDER_ERROR)),
            )
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts)
            .containsExactly(Attempt(a, AttemptOutcome.RATE_LIMITED), Attempt(b, AttemptOutcome.PROVIDER_ERROR))
    }

    @Test
    fun `an exception from the provider is classified provider-error and never propagates`() {
        val p = FakeProvider(script = mapOf(a to { error("boom") }, b to answer("B")))
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Answered
        assertThat(r.attempts)
            .containsExactly(Attempt(a, AttemptOutcome.PROVIDER_ERROR), Attempt(b, AttemptOutcome.ANSWERED))
    }

    @Test
    fun `no provider key fails as ai-not-configured with zero attempts`() {
        val p = FakeProvider(configured = false, script = mapOf(a to answer("A")))
        val r = loop(p).call(listOf(a, b), ModelSlotKind.TEXT, request) as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.AI_NOT_CONFIGURED)
        assertThat(r.attempts).isEmpty()
        assertThat(p.calls).isEmpty()
    }
}
