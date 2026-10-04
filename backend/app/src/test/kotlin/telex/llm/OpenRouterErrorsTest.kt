package telex.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import telex.llm.AttemptOutcome.CONTENT_REFUSED
import telex.llm.AttemptOutcome.INVALID_REQUEST
import telex.llm.AttemptOutcome.PROVIDER_ERROR
import telex.llm.AttemptOutcome.RATE_LIMITED
import telex.llm.AttemptOutcome.TIMEOUT
import telex.llm.AttemptOutcome.TOO_LARGE
import telex.llm.AttemptOutcome.UNAVAILABLE
import telex.llm.internal.openrouter.OpenRouterErrors

/** AC-224 / AC-228: status first, body second; an unparsable body is classified by status alone. */
class OpenRouterErrorsTest {
    private fun err(
        code: Int,
        msg: String,
    ) = """{"error":{"code":$code,"message":"$msg"}}"""

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "429, rate limit, RATE_LIMITED",
        "408, request timeout, TIMEOUT",
        "413, payload too large, TOO_LARGE",
        "404, model not found, UNAVAILABLE",
        "503, No endpoints found for acme/chat, UNAVAILABLE",
        "502, No allowed providers are available for the selected model, UNAVAILABLE",
        "500, internal error, PROVIDER_ERROR",
        "502, bad gateway, PROVIDER_ERROR",
        "503, overloaded, PROVIDER_ERROR",
        "400, This model's maximum context length is 8000 tokens, TOO_LARGE",
        "400, Input is too long for requested model - context length exceeded, TOO_LARGE",
        "403, Your input was flagged by moderation, CONTENT_REFUSED",
        "400, Request blocked by content policy, CONTENT_REFUSED",
        "400, messages must be an array, INVALID_REQUEST",
        "400, acme/old-chat is not a valid model ID, UNAVAILABLE",
        "400, Model not found: acme/old-chat, UNAVAILABLE",
        "400, No such model acme/old-chat, UNAVAILABLE",
        "400, No endpoints found for acme/old-chat, UNAVAILABLE",
        "400, not a valid model but the context length is exceeded, TOO_LARGE",
        "400, not a valid model - flagged by moderation, CONTENT_REFUSED",
        "422, unprocessable entity, INVALID_REQUEST",
        "401, invalid api key, PROVIDER_ERROR",
        "402, insufficient credits, PROVIDER_ERROR",
    )
    fun `maps status and body to an outcome`(
        status: Int,
        message: String,
        expected: AttemptOutcome,
    ) {
        assertThat(OpenRouterErrors.classify(status, err(status, message))).isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0} with a body that is not json")
    @CsvSource(
        "400, INVALID_REQUEST",
        "422, INVALID_REQUEST",
        "413, TOO_LARGE",
        "429, RATE_LIMITED",
        "408, TIMEOUT",
        "404, UNAVAILABLE",
        "500, PROVIDER_ERROR",
        "502, PROVIDER_ERROR",
    )
    fun `an unparsable or empty body is classified by status alone`(
        status: Int,
        expected: AttemptOutcome,
    ) {
        assertThat(OpenRouterErrors.classify(status, "<html>oops</html>")).isEqualTo(expected)
        assertThat(OpenRouterErrors.classify(status, null)).isEqualTo(expected)
        assertThat(OpenRouterErrors.classify(status, "")).isEqualTo(expected)
    }

    @org.junit.jupiter.api.Test
    fun `status wins over the body - a 429 saying context length is still rate limited`() {
        assertThat(OpenRouterErrors.classify(429, err(429, "context length"))).isEqualTo(RATE_LIMITED)
        assertThat(setOf(CONTENT_REFUSED, INVALID_REQUEST, PROVIDER_ERROR, TIMEOUT, TOO_LARGE, UNAVAILABLE)).hasSize(6)
    }
}
