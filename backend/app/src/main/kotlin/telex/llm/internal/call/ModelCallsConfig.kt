package telex.llm.internal.call

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import telex.llm.ModelCalls
import telex.llm.ModelCatalog
import telex.llm.internal.openrouter.LlmProperties

@Configuration(proxyBeanMethods = false)
class ModelCallsConfig {
    @Bean
    fun modelCalls(
        provider: ModelProvider,
        catalog: ModelCatalog,
        properties: LlmProperties,
    ): ModelCalls = FallbackLoop(provider, catalog, properties.attemptTimeout)
}
