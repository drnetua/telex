package telex.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import telex.TestcontainersConfiguration
import telex.llm.internal.openrouter.LlmProperties
import telex.llm.internal.openrouter.OpenRouterProperties
import java.time.Duration

/** AC-225: with no overrides the installation settings default as the SAD says, and boot works without a key. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class LlmPropertiesIT {
    @Autowired lateinit var llm: LlmProperties

    @Autowired lateinit var openRouter: OpenRouterProperties

    @Test
    fun `defaults come from application yaml and a missing key is not a startup failure`() {
        assertThat(llm.attemptTimeout).isEqualTo(Duration.ofSeconds(60))
        assertThat(llm.catalog.refreshInterval).isEqualTo(Duration.ofHours(24))
        assertThat(llm.catalog.retryInterval).isEqualTo(Duration.ofMinutes(5))
        assertThat(openRouter.baseUrl).isEqualTo("https://openrouter.ai/api/v1")
        assertThat(openRouter.apiKey).isNullOrEmpty()
    }
}
