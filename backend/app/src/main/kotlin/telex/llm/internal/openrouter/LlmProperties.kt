package telex.llm.internal.openrouter

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Defaults live in application.yaml. */
@ConfigurationProperties("telex.llm")
data class LlmProperties(
    val attemptTimeout: Duration = Duration.ZERO,
    val catalog: Catalog = Catalog(),
) {
    data class Catalog(
        val refreshInterval: Duration = Duration.ZERO,
        val retryInterval: Duration = Duration.ZERO,
    )
}
