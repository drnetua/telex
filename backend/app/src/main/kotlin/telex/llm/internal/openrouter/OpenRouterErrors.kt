package telex.llm.internal.openrouter

import telex.llm.AttemptOutcome
import tools.jackson.databind.json.JsonMapper

/**
 * Maps an OpenRouter error response to an outcome: HTTP status first, the error message second.
 * An unparsable or empty body is classified by status alone. Never keeps or logs the body.
 */
object OpenRouterErrors {
    private const val BAD_REQUEST = 400
    private const val BAD_GATEWAY = 502
    private const val UNAVAILABLE_STATUS = 503

    /** Statuses that decide on their own; 401 / 402 (key, credit) and other 5xx fall through to PROVIDER_ERROR. */
    private val byStatus =
        mapOf(
            429 to AttemptOutcome.RATE_LIMITED,
            408 to AttemptOutcome.TIMEOUT,
            413 to AttemptOutcome.TOO_LARGE,
            404 to AttemptOutcome.UNAVAILABLE,
            403 to AttemptOutcome.CONTENT_REFUSED,
            422 to AttemptOutcome.INVALID_REQUEST,
        )

    private val mapper = JsonMapper.builder().build()
    private val tooLarge = listOf("context length", "too long", "too large", "maximum context")
    private val refused = listOf("moderation", "content policy", "flagged")
    private val noProvider = listOf("no endpoints", "no allowed providers")

    fun classify(
        status: Int,
        body: String?,
    ): AttemptOutcome {
        val message = message(body)
        return byStatus[status]
            ?: when (status) {
                BAD_REQUEST -> {
                    classifyBadRequest(message)
                }

                BAD_GATEWAY, UNAVAILABLE_STATUS -> {
                    if (message.hasAny(noProvider)) AttemptOutcome.UNAVAILABLE else AttemptOutcome.PROVIDER_ERROR
                }

                else -> {
                    AttemptOutcome.PROVIDER_ERROR
                }
            }
    }

    private fun classifyBadRequest(message: String) =
        when {
            message.hasAny(tooLarge) -> AttemptOutcome.TOO_LARGE
            message.hasAny(refused) -> AttemptOutcome.CONTENT_REFUSED
            else -> AttemptOutcome.INVALID_REQUEST
        }

    private fun String.hasAny(words: List<String>) = words.any { contains(it) }

    private fun message(body: String?): String =
        if (body.isNullOrBlank()) {
            ""
        } else {
            runCatching {
                mapper
                    .readTree(body)
                    .path("error")
                    .path("message")
                    .asString("")
                    .lowercase()
            }.getOrDefault("")
        }
}
