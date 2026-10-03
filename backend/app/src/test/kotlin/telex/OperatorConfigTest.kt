package telex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

/** AC-119 / AC-120: the Operator's Telegram setup is driven only by `TELEX_*` variables with safe defaults. */
class OperatorConfigTest {
    private fun environment(
        vararg files: String,
        variables: Map<String, Any> = emptyMap(),
    ): StandardEnvironment {
        val env = StandardEnvironment()
        env.propertySources.addFirst(
            org.springframework.core.env
                .MapPropertySource("vars", variables),
        )
        files.forEach { file ->
            YamlPropertySourceLoader().load(file, ClassPathResource(file)).forEach { env.propertySources.addLast(it) }
        }
        return env
    }

    @Test
    fun `an installation with no Telegram variables defaults to the real adapter, no credentials and no master key`() {
        val env = environment("application.yaml")

        assertThat(env.getProperty("telex.telegram.adapter")).isEqualTo("tdlight")
        assertThat(env.getProperty("telex.telegram.api-id")).isNullOrEmpty()
        assertThat(env.getProperty("telex.telegram.api-hash")).isNullOrEmpty()
        assertThat(env.getProperty("telex.telegram.max-accounts-per-owner")).isEqualTo("3")
        assertThat(env.getProperty("telex.telegram.sessions-dir")).isEqualTo("/var/lib/telex/tdlib")
        assertThat(env.getProperty("telex.master-key")).isNullOrEmpty()
        assertThat(env.getProperty("telex.master-key-reset")).isEqualTo("false")
    }

    @Test
    fun `the Operator variables map onto the telex properties`() {
        val env =
            environment(
                "application.yaml",
                variables =
                    mapOf(
                        "TELEX_TELEGRAM_API_ID" to "12345",
                        "TELEX_TELEGRAM_API_HASH" to "abc",
                        "TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER" to "5",
                        "TELEX_MASTER_KEY" to "a2V5",
                        "TELEX_MASTER_KEY_RESET" to "true",
                        "TELEX_TELEGRAM_ADAPTER" to "fake",
                    ),
            )

        assertThat(env.getProperty("telex.telegram.api-id")).isEqualTo("12345")
        assertThat(env.getProperty("telex.telegram.api-hash")).isEqualTo("abc")
        assertThat(env.getProperty("telex.telegram.max-accounts-per-owner")).isEqualTo("5")
        assertThat(env.getProperty("telex.master-key")).isEqualTo("a2V5")
        assertThat(env.getProperty("telex.master-key-reset")).isEqualTo("true")
        assertThat(env.getProperty("telex.telegram.adapter")).isEqualTo("fake")
    }

    @Test
    fun `the local profile uses the fake adapter unless the Operator picks another`() {
        val local = environment("application-local.yaml", "application.yaml")
        assertThat(local.getProperty("telex.telegram.adapter")).isEqualTo("fake")

        val real =
            environment(
                "application-local.yaml",
                "application.yaml",
                variables = mapOf("TELEX_TELEGRAM_ADAPTER" to "tdlight"),
            )
        assertThat(real.getProperty("telex.telegram.adapter")).isEqualTo("tdlight")
    }
}
