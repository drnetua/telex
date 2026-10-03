package telex.llm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import telex.TestcontainersConfiguration
import telex.llm.internal.call.FallbackLoop
import telex.llm.internal.call.ModelProvider
import telex.llm.internal.call.ProviderResult
import telex.llm.internal.openrouter.OpenRouterProperties
import telex.llm.internal.openrouter.OpenRouterProvider
import java.math.BigDecimal
import java.time.Duration
import java.util.Base64

/** AC-224 / AC-228 against WireMock standing in for OpenRouter: every slot, each error class, the loop end to end. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OpenRouterProviderIT {
    @Autowired lateinit var beanProvider: ModelProvider

    private val key = "sk-or-secret-key-123"
    private val promptText = "PROMPT-SECRET-4711"
    private val answerText = "ANSWER-SECRET-0815"
    private val a = ModelId("acme/a")
    private val b = ModelId("acme/b")
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3)
    private val logs = ListAppender<ILoggingEvent>()
    private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    companion object {
        private val wm = WireMockServer(options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun up() = wm.start()

        @AfterAll
        @JvmStatic
        fun down() = wm.stop()
    }

    @BeforeEach
    fun setUp() {
        wm.resetAll()
        logs.start()
        root.addAppender(logs)
        root.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        root.detachAppender(logs)
    }

    private fun provider(apiKey: String? = key) =
        OpenRouterProvider(OpenRouterProperties(apiKey, "http://localhost:${wm.port()}"))

    private fun model(
        id: ModelId,
        takes: Set<Modality>,
        produces: Set<Modality>,
    ) = CatalogModel(id, id.value, "acme", takes, produces, BigDecimal.ONE, BigDecimal.ONE, null, 8000)

    private fun loop(
        takes: Set<Modality> = setOf(Modality.TEXT),
        produces: Set<Modality> = setOf(Modality.TEXT),
        timeout: Duration = Duration.ofSeconds(10),
        apiKey: String? = key,
    ): FallbackLoop {
        val models = listOf(a, b).associateWith { model(it, takes, produces) }
        val catalog =
            object : ModelCatalog {
                override fun snapshot() = CatalogSnapshot(models, null, null, CatalogState.CURRENT)

                override fun find(modelId: ModelId) = models[modelId]
            }
        return FallbackLoop(provider(apiKey), catalog, timeout)
    }

    private val textReq = SlotRequest.Text(listOf(ChatMessage("user", promptText)))

    private fun chatOk(text: String = answerText) =
        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
            """{"id":"x","choices":[{"index":0,"finish_reason":"stop",""" +
                """"message":{"role":"assistant","content":"$text"}}]}""",
        )

    private fun stub(
        model: ModelId,
        response: com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder,
    ) = wm.stubFor(
        post(urlEqualTo("/chat/completions")).withRequestBody(containing("\"${model.value}\"")).willReturn(response),
    )

    private fun error(
        status: Int,
        message: String = "boom",
    ) = aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody("""{"error":{"code":$status,"message":"$message"}}""")

    private fun logged() =
        logs.list.joinToString("\n") { e ->
            e.formattedMessage + " " +
                (e.throwableProxy?.let { t -> t.message + t.stackTraceElementProxyArray.joinToString() } ?: "")
        }

    private fun assertNothingSensitiveLogged() {
        assertThat(logged()).doesNotContain(key).doesNotContain(promptText).doesNotContain(answerText)
    }

    @Test
    fun `text slot posts to chat completions with the bearer key and returns the answer text`() {
        stub(a, chatOk())
        val r = loop().call(listOf(a), ModelSlotKind.TEXT, textReq)
        assertThat(r).isInstanceOf(ModelCallResult.Answered::class.java)
        r as ModelCallResult.Answered
        assertThat(r.answer).isEqualTo(SlotAnswer.Text(answerText))
        assertThat(r.answeredBy).isEqualTo(a)
        assertThat(r.fallback).isFalse()
        wm.verify(
            postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer $key"))
                .withRequestBody(
                    equalToJson(
                        """{"model":"acme/a","messages":[{"role":"user","content":"$promptText"}]}""",
                        true,
                        true,
                    ),
                ),
        )
        assertNothingSensitiveLogged()
    }

    @Test
    fun `vision slot sends the images as data urls next to the text and returns text`() {
        stub(a, chatOk())
        val req = SlotRequest.Vision(listOf(ChatMessage("user", promptText)), listOf(ImageInput(png, "image/png")))
        val r = loop(takes = setOf(Modality.TEXT, Modality.IMAGE)).call(listOf(a), ModelSlotKind.VISION, req)
        assertThat((r as ModelCallResult.Answered).answer).isEqualTo(SlotAnswer.Text(answerText))
        val sent = wm.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).single().bodyAsString
        assertThat(
            sent,
        ).contains("image_url").contains("data:image/png;base64," + Base64.getEncoder().encodeToString(png))
        assertThat(sent).contains(promptText)
        assertNothingSensitiveLogged()
    }

    @Test
    fun `image slot asks for image output and returns decoded bytes with the media type`() {
        val url = "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
        stub(
            a,
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"",
                "images":[{"type":"image_url","image_url":{"url":"$url"}}]}}]}""",
            ),
        )
        val r =
            loop(produces = setOf(Modality.IMAGE)).call(listOf(a), ModelSlotKind.IMAGE, SlotRequest.Image(promptText))
        val answer = (r as ModelCallResult.Answered).answer as SlotAnswer.Image
        assertThat(answer.bytes).isEqualTo(png)
        assertThat(answer.mediaType).isEqualTo("image/png")
        val sent = wm.findAll(postRequestedFor(urlEqualTo("/chat/completions"))).single().bodyAsString
        assertThat(sent).contains("modalities").contains("image").contains(promptText)
        assertNothingSensitiveLogged()
    }

    @Test
    fun `an image model answering with no image is a provider error and the loop moves on`() {
        stub(a, chatOk("only words"))
        val url = "data:image/png;base64," + Base64.getEncoder().encodeToString(png)
        stub(
            b,
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                """{"choices":[{"message":{"role":"assistant","content":"",""" +
                    """"images":[{"image_url":{"url":"$url"}}]}}]}""",
            ),
        )
        val r = loop(produces = setOf(Modality.IMAGE)).call(listOf(a, b), ModelSlotKind.IMAGE, SlotRequest.Image("p"))
        r as ModelCallResult.Answered
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.PROVIDER_ERROR),
            Attempt(b, AttemptOutcome.ANSWERED),
        )
        assertThat(r.fallback).isTrue()
    }

    @ParameterizedTest(name = "{0} on model A moves on and B answers")
    @CsvSource(
        "429, rate limit, RATE_LIMITED",
        "500, server error, PROVIDER_ERROR",
        "404, model not found, UNAVAILABLE",
        "503, No endpoints found for acme/a, UNAVAILABLE",
        "413, payload too large, TOO_LARGE",
        "400, maximum context length is 8000 tokens, TOO_LARGE",
        "408, request timeout, TIMEOUT",
    )
    fun `a move-on error on A is answered by B and the next request starts from A again`(
        status: Int,
        message: String,
        expected: AttemptOutcome,
    ) {
        stub(a, error(status, message))
        stub(b, chatOk())
        val l = loop()
        repeat(2) {
            val r = l.call(listOf(a, b), ModelSlotKind.TEXT, textReq) as ModelCallResult.Answered
            assertThat(r.answeredBy).isEqualTo(b)
            assertThat(r.fallback).isTrue()
            assertThat(r.attempts).containsExactly(Attempt(a, expected), Attempt(b, AttemptOutcome.ANSWERED))
        }
        assertThat(wm.findAll(postRequestedFor(urlEqualTo("/chat/completions")).withRequestBody(containing("acme/a"))))
            .hasSize(2)
        assertNothingSensitiveLogged()
    }

    @Test
    fun `a stalled model A times out and B answers`() {
        stub(a, chatOk().withFixedDelay(3000))
        stub(b, chatOk())
        val r = loop(timeout = Duration.ofMillis(500)).call(listOf(a, b), ModelSlotKind.TEXT, textReq)
        r as ModelCallResult.Answered
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.TIMEOUT), Attempt(b, AttemptOutcome.ANSWERED))
    }

    @ParameterizedTest(name = "{0} {1} stops the loop at once")
    @CsvSource(
        "403, Your input was flagged by moderation, CONTENT_REFUSED",
        "400, Request blocked by content policy, CONTENT_REFUSED",
        "400, messages must be an array, INVALID_REQUEST",
        "422, unprocessable entity, INVALID_REQUEST",
    )
    fun `content refusal and invalid request stop without trying B`(
        status: Int,
        message: String,
        expected: AttemptOutcome,
    ) {
        stub(a, error(status, message))
        stub(b, chatOk())
        val r = loop().call(listOf(a, b), ModelSlotKind.TEXT, textReq)
        r as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(Attempt(a, expected))
        assertThat(wm.findAll(postRequestedFor(urlEqualTo("/chat/completions")).withRequestBody(containing("acme/b"))))
            .isEmpty()
        assertNothingSensitiveLogged()
    }

    @Test
    fun `a 200 finished by the content filter is a content refusal and stops the loop`() {
        stub(
            a,
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                """{"choices":[{"finish_reason":"content_filter","message":{"role":"assistant","content":""}}]}""",
            ),
        )
        stub(b, chatOk())
        val r = loop().call(listOf(a, b), ModelSlotKind.TEXT, textReq) as ModelCallResult.Failed
        assertThat(r.attempts).containsExactly(Attempt(a, AttemptOutcome.CONTENT_REFUSED))
    }

    @Test
    fun `both models failing lists each attempt with its reason`() {
        stub(a, error(429, "rate limit"))
        stub(b, error(500, "server error"))
        val r = loop().call(listOf(a, b), ModelSlotKind.TEXT, textReq) as ModelCallResult.Failed
        assertThat(r.reason).isEqualTo(ModelCallFailure.NO_MODEL_ANSWERED)
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.RATE_LIMITED),
            Attempt(b, AttemptOutcome.PROVIDER_ERROR),
        )
    }

    @Test
    fun `an unparsable error body is classified by the status alone`() {
        stub(a, aResponse().withStatus(502).withBody("<html>bad gateway</html>"))
        stub(b, aResponse().withStatus(400).withBody("???"))
        val r = loop().call(listOf(a, b), ModelSlotKind.TEXT, textReq) as ModelCallResult.Failed
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.PROVIDER_ERROR),
            Attempt(b, AttemptOutcome.INVALID_REQUEST),
        )
    }

    @Test
    fun `an unparsable success body or a dropped connection is a provider error, never an exception`() {
        stub(a, aResponse().withStatus(200).withBody("not json"))
        stub(b, aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
        val r = loop().call(listOf(a, b), ModelSlotKind.TEXT, textReq) as ModelCallResult.Failed
        assertThat(r.attempts).containsExactly(
            Attempt(a, AttemptOutcome.PROVIDER_ERROR),
            Attempt(b, AttemptOutcome.PROVIDER_ERROR),
        )
        assertNothingSensitiveLogged()
    }

    @Test
    fun `without a key nothing is called and the result is not-configured`() {
        stub(a, chatOk())
        val r = loop(apiKey = null).call(listOf(a), ModelSlotKind.TEXT, textReq)
        assertThat(r).isEqualTo(ModelCallResult.Failed(ModelCallFailure.AI_NOT_CONFIGURED, emptyList()))
        assertThat(wm.allServeEvents).isEmpty()
    }

    @Test
    fun `the context boots without a key and exposes the adapter as the ModelProvider bean`() {
        assertThat(beanProvider).isInstanceOf(OpenRouterProvider::class.java)
        assertThat(beanProvider.isConfigured()).isFalse() // test env sets no TELEX_OPENROUTER_API_KEY
        assertThat(provider().isConfigured()).isTrue()
        assertThat(provider(null).isConfigured()).isFalse()
        assertThat(provider("  ").isConfigured()).isFalse()
        assertThat(ProviderResult.Failure(AttemptOutcome.MISSING).outcome.movesOn).isTrue()
    }
}
