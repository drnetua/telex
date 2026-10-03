package telex.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.llm.internal.openrouter.ModelListParser

class ModelListParserTest {
    private val fixture =
        checkNotNull(javaClass.getResource("/openrouter/models.json")) { "fixture missing" }.readText()

    private fun one(
        id: String = "acme/m",
        pricing: String = """{"prompt":"0.000001","completion":"0.000002"}""",
        context: String = "8192",
        input: String = """["text"]""",
        output: String = """["text"]""",
        extra: String = "",
    ) = """{"data":[{"id":"$id","name":"M","context_length":$context,$extra
            "architecture":{"input_modalities":$input,"output_modalities":$output},
            "pricing":$pricing}]}"""

    private fun byId(id: String) = ModelListParser.parse(fixture).models.single { it.modelId == ModelId(id) }

    @Test
    fun `price is shifted from per token to per million exactly`() {
        val m = byId("openai/gpt-4o-mini")
        assertThat(m.inputPerMtok).isEqualByComparingTo("0.150000")
        assertThat(m.outputPerMtok).isEqualByComparingTo("0.600000")
        assertThat(m.contextLength).isEqualTo(128000)
    }

    @Test
    fun `provider comes from the id prefix and name is kept`() {
        val m = byId("openai/gpt-4o-mini")
        assertThat(m.provider).isEqualTo("openai")
        assertThat(m.name).isNotBlank()
    }

    @Test
    fun `capabilities map to modalities and slots`() {
        assertThat(byId("openai/gpt-4o-mini").slots)
            .containsExactlyInAnyOrder(ModelSlotKind.TEXT, ModelSlotKind.VISION)
        assertThat(byId("inclusionai/ling-3.1-flash").slots).containsExactly(ModelSlotKind.TEXT)
        assertThat(byId("google/gemini-2.5-flash-image").fits(ModelSlotKind.IMAGE)).isTrue()
    }

    @Test
    fun `free model has price zero not null`() {
        assertThat(byId("inclusionai/ling-3.1-flash").inputPerMtok).isEqualByComparingTo("0")
    }

    @Test
    fun `audio-only models are skipped and reported`() {
        val r = ModelListParser.parse(fixture)
        assertThat(r.models.map { it.modelId.value }).doesNotContain("openai/gpt-audio", "google/lyria-3-clip-preview")
        assertThat(r.skipped.map { it.id }).contains("openai/gpt-audio", "google/lyria-3-clip-preview")
    }

    @Test
    fun `every parsed model fits at least one slot`() {
        assertThat(ModelListParser.parse(fixture).models).isNotEmpty().allMatch { it.slots.isNotEmpty() }
    }

    @Test
    fun `negative sentinel price becomes unknown`() {
        assertThat(byId("openrouter/auto").inputPerMtok).isNull()
    }

    @Test
    fun `missing or non-decimal price is null and the model stays`() {
        val r = ModelListParser.parse(one(pricing = """{"prompt":"abc"}"""))
        val m = r.models.single()
        assertThat(m.inputPerMtok).isNull()
        assertThat(m.outputPerMtok).isNull()
    }

    @Test
    fun `negative price is unknown`() {
        val m = ModelListParser.parse(one(pricing = """{"prompt":"-0.5","completion":"-1"}""")).models.single()
        assertThat(m.inputPerMtok).isNull()
        assertThat(m.outputPerMtok).isNull()
    }

    @Test
    fun `non-positive or missing context length is null`() {
        assertThat(
            ModelListParser
                .parse(one(context = "0"))
                .models
                .single()
                .contextLength,
        ).isNull()
        assertThat(
            ModelListParser
                .parse(one(context = "-5"))
                .models
                .single()
                .contextLength,
        ).isNull()
        assertThat(
            ModelListParser
                .parse(one(context = "null"))
                .models
                .single()
                .contextLength,
        ).isNull()
    }

    @Test
    fun `unknown modality is skipped`() {
        val r = ModelListParser.parse(one(input = """["hologram"]"""))
        assertThat(r.models).isEmpty()
        assertThat(r.skipped.map { it.id }).containsExactly("acme/m")
    }

    @Test
    fun `missing modalities are skipped`() {
        val json = """{"data":[{"id":"acme/m","name":"M","pricing":{}}]}"""
        val r = ModelListParser.parse(json)
        assertThat(r.models).isEmpty()
        assertThat(r.skipped).hasSize(1)
    }

    @Test
    fun `id longer than 200 characters is skipped`() {
        val r = ModelListParser.parse(one(id = "acme/" + "x".repeat(196)))
        assertThat(r.models).isEmpty()
        assertThat(r.skipped).hasSize(1)
        assertThat(ModelListParser.parse(one(id = "acme/" + "x".repeat(195))).models).hasSize(1)
    }

    @Test
    fun `one bad entry does not drop the others`() {
        val json = """{"data":[{"id":5},{"id":"acme/ok","name":"Ok","context_length":10,
            "architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{}}]}"""
        val r = ModelListParser.parse(json)
        assertThat(r.models.map { it.modelId.value }).containsExactly("acme/ok")
        assertThat(r.skipped).hasSize(1)
    }
}
