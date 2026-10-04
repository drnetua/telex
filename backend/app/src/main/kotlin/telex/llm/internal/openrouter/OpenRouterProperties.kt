package telex.llm.internal.openrouter

import org.springframework.boot.context.properties.ConfigurationProperties

/** Installation settings for the model provider; the key is a secret and is never logged. */
@ConfigurationProperties("telex.llm.openrouter")
data class OpenRouterProperties(
    val apiKey: String? = null,
    val baseUrl: String = "https://openrouter.ai/api/v1",
) {
    val configured: Boolean get() = !apiKey.isNullOrBlank()

    override fun toString() = "OpenRouterProperties(baseUrl=$baseUrl)"
}
