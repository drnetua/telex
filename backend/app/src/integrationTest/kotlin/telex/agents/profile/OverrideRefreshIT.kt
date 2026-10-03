package telex.agents.profile

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.agents.internal.profile.OverrideValidator
import telex.agents.internal.profile.SystemProfiles
import telex.llm.ModelCatalogRefreshed
import telex.llm.internal.catalog.CatalogHolder
import telex.llm.internal.catalog.CatalogRefresher
import telex.llm.internal.catalog.CatalogSnapshotStore
import telex.llm.internal.openrouter.LlmProperties
import telex.llm.internal.openrouter.OpenRouterModelsClient
import telex.llm.internal.openrouter.OpenRouterProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** AC-227: the real refresher + holder + validator; a refresh re-checks the overrides against the NEW catalog. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OverrideRefreshIT {
    @Autowired lateinit var store: CatalogSnapshotStore

    @Autowired lateinit var jdbc: JdbcTemplate

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: ZoneId) = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-10-03T10:00:00Z"))
    private val logs = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(OverrideValidator::class.java) as Logger

    private fun list(vararg ids: String) =
        """{"data":[${ids.joinToString(",") {
            """{"id":"$it","name":"$it","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
            "pricing":{"prompt":"0.000001","completion":"0.000002"},"context_length":8000}"""
        }}]}"""

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
        jdbc.update("DELETE FROM model_catalog_entry")
        jdbc.update("DELETE FROM model_catalog_state")
        logs.start()
        logger.addAppender(logs)
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logs)
    }

    private fun serve(vararg ids: String) =
        wm.stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(200).withBody(list(*ids))))

    private fun warns() = logs.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun wire(): Triple<CatalogRefresher, CatalogHolder, OverrideValidator> {
        val holder = CatalogHolder(store).also { it.load() }
        val shipped = mapOf("balanced" to mapOf("text" to listOf("ok/text", "shipped/model")))
        val validator = OverrideValidator(SystemProfiles.merge(shipped, emptyMap()), holder)
        // the real publisher delivers after commit; here the listener runs at publish time, the earliest it
        // could run, so the holder must already hold the new snapshot by then
        val publisher = ApplicationEventPublisher { validator.onCatalogRefreshed(it as ModelCatalogRefreshed) }
        val props = OpenRouterProperties("sk-or-key", "http://localhost:${wm.port()}")
        val llm =
            LlmProperties(Duration.ofSeconds(60), LlmProperties.Catalog(Duration.ofHours(24), Duration.ofMinutes(5)))
        val refresher =
            CatalogRefresher(
                OpenRouterModelsClient(props),
                store,
                holder,
                props,
                llm,
                clock,
                publisher,
                SimpleMeterRegistry(),
            )
        return Triple(refresher, holder, validator)
    }

    private fun warnsAbout(id: String) = warns().filter { it.contains("'$id'") }

    @Test
    fun `a refresh that drops a shipped model warns on that same refresh and a restore stops the warning`() {
        serve("ok/text", "shipped/model")
        val (refresher, _, _) = wire()
        refresher.start()
        assertThat(warnsAbout("shipped/model")).isEmpty()

        serve("ok/text")
        clock.now = clock.now.plus(Duration.ofHours(24))
        refresher.refreshIfDue()
        assertThat(warnsAbout("shipped/model")).hasSize(1)

        serve("ok/text", "shipped/model")
        clock.now = clock.now.plus(Duration.ofHours(24))
        refresher.refreshIfDue()
        assertThat(warnsAbout("shipped/model")).hasSize(1) // no new warning
    }

    @Test
    fun `startup logs each warning exactly once`() {
        serve("ok/text")
        val (refresher, _, validator) = wire()
        refresher.start() // @Order(1)
        validator.onReady() // after the refresher, as at startup

        assertThat(warnsAbout("shipped/model")).hasSize(1)
    }
}
