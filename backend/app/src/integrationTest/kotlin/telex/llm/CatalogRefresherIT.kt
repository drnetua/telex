package telex.llm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.springframework.transaction.support.TransactionSynchronizationManager
import telex.TestcontainersConfiguration
import telex.llm.internal.catalog.CatalogHolder
import telex.llm.internal.catalog.CatalogRefresher
import telex.llm.internal.catalog.CatalogSnapshotStore
import telex.llm.internal.openrouter.LlmProperties
import telex.llm.internal.openrouter.OpenRouterModelsClient
import telex.llm.internal.openrouter.OpenRouterProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** AC-212 / AC-225 / AC-226: refresh at start, every 24 h, every 5 min after a failure; never real sleeps. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CatalogRefresherIT {
    @Autowired lateinit var store: CatalogSnapshotStore

    @Autowired lateinit var jdbc: JdbcTemplate

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this

        override fun instant(): Instant = now
    }

    private val t0 = Instant.parse("2026-10-03T10:00:00Z")
    private val key = "sk-or-secret-key-123"
    private val clock = MutableClock(t0)
    private val published = mutableListOf<Any>()
    private val meters = SimpleMeterRegistry()
    private val publishedInTx = mutableListOf<Boolean>()
    private val publisher =
        ApplicationEventPublisher {
            published += it
            publishedInTx += TransactionSynchronizationManager.isActualTransactionActive()
        }
    private val logs = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger("telex.llm") as Logger
    private var originalLevel: Level? = null

    private val twoModels =
        """{"data":[
        {"id":"openai/gpt-x","name":"GPT X","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
         "pricing":{"prompt":"0.000001","completion":"0.000002"},"context_length":128000},
        {"id":"acme/chat","name":"Chat","architecture":{"input_modalities":["text"],"output_modalities":["text"]},
         "pricing":{"prompt":"0.000001","completion":"0.000002"},"context_length":8000}]}"""

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
        originalLevel = logger.level
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logs)
        logger.level = originalLevel
    }

    private fun ok(body: String = twoModels) =
        wm.stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(200).withBody(body)))

    private fun fail(status: Int = 503) =
        wm.stubFor(get(urlEqualTo("/models")).willReturn(aResponse().withStatus(status)))

    private fun fetches() = wm.findAll(getRequestedFor(urlEqualTo("/models"))).size

    private fun newApp(apiKey: String? = key): Pair<CatalogRefresher, CatalogHolder> {
        val holder = CatalogHolder(store).also { it.load() }
        val props = OpenRouterProperties(apiKey, "http://localhost:${wm.port()}")
        val llm =
            LlmProperties(
                Duration.ofSeconds(60),
                LlmProperties.Catalog(Duration.ofHours(24), Duration.ofMinutes(5)),
            )
        val refresher =
            CatalogRefresher(OpenRouterModelsClient(props), store, holder, props, llm, clock, publisher, meters)
        return refresher to holder
    }

    private fun warnText() = logs.list.filter { it.level == Level.WARN }.joinToString("\n") { it.formattedMessage }

    @Test
    fun `start loads the catalog, publishes the event and schedules the next run in 24 h`() {
        ok()
        val (refresher, holder) = newApp()
        refresher.start()
        val s = holder.snapshot()
        assertThat(s.models).containsOnlyKeys(ModelId("openai/gpt-x"), ModelId("acme/chat"))
        assertThat(s.refreshedAt).isEqualTo(t0)
        assertThat(s.state).isEqualTo(CatalogState.CURRENT)
        assertThat(published).containsExactly(ModelCatalogRefreshed(t0, 2))
        assertThat(publishedInTx).containsExactly(true) // in the transaction that replaces the snapshot
        assertThat(refresher.nextRunAt).isEqualTo(t0.plus(Duration.ofHours(24)))
        assertThat(
            meters
                .get("telex.llm.catalog.refresh")
                .tag("outcome", "ok")
                .counter()
                .count(),
        ).isEqualTo(1.0)
        wm.verify(getRequestedFor(urlEqualTo("/models")).withHeader("Authorization", equalTo("Bearer $key")))
    }

    @Test
    fun `a tick before 24 h does not fetch, at 24 h it refreshes again`() {
        ok()
        val (refresher, holder) = newApp()
        refresher.start()
        clock.now = t0.plus(Duration.ofHours(23))
        refresher.refreshIfDue()
        assertThat(fetches()).isEqualTo(1)
        assertThat(meters.get("telex.llm.catalog.age.hours").gauge().value()).isEqualTo(23.0)
        assertThat(meters.get("telex.llm.catalog.models").gauge().value()).isEqualTo(2.0)
        clock.now = t0.plus(Duration.ofHours(24))
        refresher.refreshIfDue()
        assertThat(fetches()).isEqualTo(2)
        assertThat(holder.snapshot().refreshedAt).isEqualTo(clock.now)
        assertThat(refresher.nextRunAt).isEqualTo(clock.now.plus(Duration.ofHours(24)))
    }

    @Test
    fun `a failed refresh keeps the last snapshot, retries in 5 min and then recovers`() {
        ok()
        val (refresher, holder) = newApp()
        refresher.start()
        published.clear()
        fail()
        clock.now = t0.plus(Duration.ofHours(24))
        refresher.refreshIfDue()
        val s = holder.snapshot()
        assertThat(s.models).hasSize(2)
        assertThat(s.refreshedAt).isEqualTo(t0)
        assertThat(s.failedAt).isEqualTo(clock.now)
        assertThat(s.state).isEqualTo(CatalogState.UPDATE_FAILED)
        assertThat(published).isEmpty()
        assertThat(refresher.nextRunAt).isEqualTo(clock.now.plus(Duration.ofMinutes(5)))
        assertThat(warnText()).isNotBlank().doesNotContain(key)
        assertThat(
            meters
                .get("telex.llm.catalog.refresh")
                .tag("outcome", "failed")
                .counter()
                .count(),
        ).isEqualTo(1.0)

        ok()
        clock.now = clock.now.plus(Duration.ofMinutes(5))
        refresher.refreshIfDue()
        assertThat(holder.snapshot().state).isEqualTo(CatalogState.CURRENT)
        assertThat(refresher.nextRunAt).isEqualTo(clock.now.plus(Duration.ofHours(24)))
    }

    @Test
    fun `first start with the provider down shows not-loaded and retries every 5 min without limit`() {
        fail()
        val (refresher, holder) = newApp()
        refresher.start()
        assertThat(holder.snapshot().state).isEqualTo(CatalogState.NOT_LOADED)
        assertThat(holder.snapshot().models).isEmpty()
        repeat(3) {
            assertThat(refresher.nextRunAt).isEqualTo(clock.now.plus(Duration.ofMinutes(5)))
            clock.now = clock.now.plus(Duration.ofMinutes(5))
            refresher.refreshIfDue()
        }
        assertThat(fetches()).isEqualTo(4)
        assertThat(holder.snapshot().failedAt).isEqualTo(clock.now)
    }

    @Test
    fun `a restart with the provider down keeps serving the stored snapshot`() {
        ok()
        newApp().first.start()
        fail()
        clock.now = t0.plus(Duration.ofHours(30))
        val (restarted, holder) = newApp() // fresh holder + refresher = restarted app
        restarted.start()
        val s = holder.snapshot()
        assertThat(s.models).containsOnlyKeys(ModelId("openai/gpt-x"), ModelId("acme/chat"))
        assertThat(s.refreshedAt).isEqualTo(t0)
        assertThat(s.state).isEqualTo(CatalogState.UPDATE_FAILED)
    }

    @Test
    fun `an empty or unparsable list is a failed refresh and never replaces a good snapshot`() {
        ok()
        val (refresher, holder) = newApp()
        refresher.start()
        var goodAt = t0
        for (bad in listOf("""{"data":[]}""", "not json at all")) {
            ok(bad)
            clock.now = clock.now.plus(Duration.ofHours(24))
            refresher.refreshIfDue()
            val s = holder.snapshot()
            assertThat(s.models).hasSize(2)
            assertThat(s.refreshedAt).isEqualTo(goodAt)
            assertThat(s.state).isEqualTo(CatalogState.UPDATE_FAILED)
            assertThat(refresher.nextRunAt).isEqualTo(clock.now.plus(Duration.ofMinutes(5)))
            clock.now = clock.now.plus(Duration.ofMinutes(5))
            ok()
            refresher.refreshIfDue()
            goodAt = clock.now // the recovery refresh legitimately replaced the snapshot
        }
    }

    @Test
    fun `no key means no fetch, no db write, a startup warning naming the setting and not-configured`() {
        ok()
        val (refresher, holder) = newApp(apiKey = null)
        refresher.start()
        refresher.refreshIfDue()
        assertThat(fetches()).isZero()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM model_catalog_state", Int::class.java)).isZero()
        assertThat(published).isEmpty()
        assertThat(warnText()).contains("TELEX_OPENROUTER_API_KEY")
        assertThat(holder.snapshot().state).isEqualTo(CatalogState.NOT_CONFIGURED)
        assertThat(holder.snapshot().models).isEmpty()
    }
}
