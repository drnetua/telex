package telex.llm.internal.openrouter

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import telex.llm.AttemptOutcome
import telex.llm.ImageInput
import telex.llm.ModelId
import telex.llm.ModelSlotKind
import telex.llm.SlotAnswer
import telex.llm.SlotRequest
import telex.llm.internal.call.ModelProvider
import telex.llm.internal.call.ProviderResult
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.Base64

/**
 * The one [ModelProvider]: text and vision go to OpenRouter's chat completions, image generation to the same
 * endpoint with image output. Never throws out of [attempt]; logs the model id and outcome only, never the key,
 * the request, the answer or an exception message (those may echo them).
 */
@Component
class OpenRouterProvider(
    private val properties: OpenRouterProperties,
) : ModelProvider {
    private val mapper = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
    private val endpoint = URI.create(properties.baseUrl.trimEnd('/') + "/chat/completions")

    override fun isConfigured(): Boolean = properties.configured

    override fun attempt(
        modelId: ModelId,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProviderResult {
        val result =
            try {
                send(modelId, slot, request)
            } catch (_: HttpTimeoutException) {
                ProviderResult.Failure(AttemptOutcome.TIMEOUT)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                ProviderResult.Failure(AttemptOutcome.TIMEOUT)
            } catch (
                @Suppress("TooGenericExceptionCaught") _: Exception,
            ) {
                ProviderResult.Failure(AttemptOutcome.PROVIDER_ERROR)
            }
        val outcome = if (result is ProviderResult.Failure) result.outcome else AttemptOutcome.ANSWERED
        log.debug("OpenRouter attempt for model {} ended {}", modelId.value, outcome)
        return result
    }

    private fun send(
        modelId: ModelId,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProviderResult {
        val payload = mapper.writeValueAsString(body(modelId, request))
        val request =
            HttpRequest
                .newBuilder(endpoint)
                .timeout(READ_TIMEOUT)
                .header("Authorization", "Bearer ${properties.apiKey.orEmpty()}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        val text = response.body().orEmpty()
        return if (status in HTTP_OK_RANGE) {
            parse(text, slot)
        } else {
            ProviderResult.Failure(OpenRouterErrors.classify(status, text))
        }
    }

    private fun body(
        modelId: ModelId,
        request: SlotRequest,
    ): Map<String, Any> =
        when (request) {
            is SlotRequest.Text -> {
                mapOf("model" to modelId.value, "messages" to request.messages.map { it.toJson() })
            }

            is SlotRequest.Vision -> {
                mapOf("model" to modelId.value, "messages" to visionMessages(request))
            }

            is SlotRequest.Image -> {
                mapOf(
                    "model" to modelId.value,
                    "modalities" to listOf("image", "text"),
                    "messages" to listOf(mapOf("role" to "user", "content" to request.prompt)),
                )
            }
        }

    private fun telex.llm.ChatMessage.toJson() = mapOf("role" to role, "content" to content)

    /** The images ride on the last message as image_url parts next to its text. */
    private fun visionMessages(request: SlotRequest.Vision): List<Map<String, Any>> {
        val head = request.messages.dropLast(1).map { it.toJson() }
        val last = request.messages.lastOrNull() ?: telex.llm.ChatMessage("user", "")
        val parts =
            buildList<Map<String, Any>> {
                add(mapOf("type" to "text", "text" to last.content))
                request.images.forEach {
                    add(
                        mapOf("type" to "image_url", "image_url" to mapOf("url" to it.dataUrl())),
                    )
                }
            }
        return head + mapOf("role" to last.role, "content" to parts)
    }

    private fun ImageInput.dataUrl() = "data:$mediaType;base64," + Base64.getEncoder().encodeToString(bytes)

    private fun parse(
        text: String,
        slot: ModelSlotKind,
    ): ProviderResult {
        val choice = mapper.readTree(text).path("choices").path(0)
        val answer =
            when {
                choice.isMissingNode -> {
                    null
                }

                choice.path("finish_reason").asString("") == "content_filter" -> {
                    return ProviderResult.Failure(AttemptOutcome.CONTENT_REFUSED)
                }

                slot == ModelSlotKind.IMAGE -> {
                    image(choice.path("message"))
                }

                else -> {
                    textAnswer(choice.path("message"))
                }
            }
        return answer?.let { ProviderResult.Answer(it) } ?: ProviderResult.Failure(AttemptOutcome.PROVIDER_ERROR)
    }

    private fun textAnswer(message: JsonNode): SlotAnswer? =
        message
            .path("content")
            .takeIf { it.isString }
            ?.asString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { SlotAnswer.Text(it) }

    private fun image(message: JsonNode): SlotAnswer? {
        val url =
            message
                .path("images")
                .path(0)
                .path("image_url")
                .path("url")
                .asString("")
        val match = DATA_URL.matchEntire(url)
        val bytes = match?.let { runCatching { Base64.getDecoder().decode(it.groupValues[2]) }.getOrNull() }
        return if (match == null || bytes == null ||
            bytes.isEmpty()
        ) {
            null
        } else {
            SlotAnswer.Image(bytes, match.groupValues[1])
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(OpenRouterProvider::class.java)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofMinutes(5)
        val HTTP_OK_RANGE = 200..299
        val DATA_URL = Regex("^data:([^;,]+);base64,(.*)$", RegexOption.DOT_MATCHES_ALL)
    }
}
