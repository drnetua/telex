package telex.llm.internal.openrouter

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/** GET {base-url}/models with the bearer key; throws on 5xx / timeout. */
@Component
class OpenRouterModelsClient(
    properties: OpenRouterProperties,
) {
    private val client =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).defaultHeader("Authorization", "Bearer ${properties.apiKey.orEmpty()}")
            .build()

    fun fetchModels(): String =
        client
            .get()
            .uri("/models")
            .retrieve()
            .body(String::class.java)
            .orEmpty()

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
