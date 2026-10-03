package telex.agents.internal.profile

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.Modality
import telex.llm.ModelCatalog
import telex.llm.ModelCatalogRefreshed
import telex.llm.ModelId
import java.time.Instant

class OverrideValidatorTest {
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(OverrideValidator::class.java) as Logger

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    private fun cm(
        id: String,
        takes: Set<Modality> = setOf(Modality.TEXT),
        produces: Set<Modality> = setOf(Modality.TEXT),
    ) = CatalogModel(ModelId(id), id, "p", takes, produces, null, null, null, null)

    private fun catalogOf(vararg m: CatalogModel) =
        object : ModelCatalog {
            val models = m.associateBy { it.modelId }

            override fun snapshot() = CatalogSnapshot(models, Instant.now(), null, CatalogState.CURRENT)

            override fun find(modelId: ModelId) = models[modelId]
        }

    private val shipped =
        mapOf(
            "fast" to
                mapOf(
                    "text" to listOf("ok/text"),
                    "vision" to listOf("ok/vision"),
                    "image" to listOf("ok/image"),
                ),
            "balanced" to
                mapOf("text" to listOf("ok/text"), "vision" to listOf("ok/vision"), "image" to listOf("ok/image")),
            "careful" to
                mapOf("text" to listOf("ok/text"), "vision" to listOf("ok/vision"), "image" to listOf("ok/image")),
        )

    private val healthy =
        catalogOf(
            cm("ok/text"),
            cm("ok/vision", takes = setOf(Modality.TEXT, Modality.IMAGE)),
            cm("ok/image", produces = setOf(Modality.TEXT, Modality.IMAGE)),
        )

    private fun validator(
        overrides: Map<String, Map<String, List<String>>>,
        catalog: ModelCatalog = healthy,
    ) = OverrideValidator(SystemProfiles.merge(shipped, overrides), catalog)

    private fun warns() = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    @Test
    fun `valid shipped models and overrides log nothing`() {
        validator(mapOf("balanced" to mapOf("text" to listOf("ok/text")))).onReady()

        assertThat(warns()).isEmpty()
    }

    @Test
    fun `model missing from the catalog warns naming profile slot and model`() {
        validator(mapOf("balanced" to mapOf("text" to listOf("ok/text", "nope/gone")))).onReady()

        assertThat(warns()).hasSize(1)
        assertThat(warns().single()).contains("balanced").contains("text").contains("nope/gone")
    }

    @Test
    fun `model that cannot do the slot warns naming profile slot and model`() {
        validator(mapOf("careful" to mapOf("vision" to listOf("ok/text")))).onReady()

        assertThat(warns()).hasSize(1)
        assertThat(warns().single()).contains("careful").contains("vision").contains("ok/text")
    }

    @Test
    fun `models beyond the third warn and are named`() {
        validator(mapOf("balanced" to mapOf("text" to List(5) { "ok/text" } + "ok/text"))).onReady()

        assertThat(warns()).hasSize(3)
        assertThat(warns()).allSatisfy { assertThat(it).contains("balanced").contains("text") }
    }

    @Test
    fun `one warning per bad model across slots`() {
        validator(
            mapOf(
                "fast" to mapOf("text" to listOf("bad/1"), "image" to listOf("bad/2")),
            ),
        ).onReady()

        assertThat(warns()).hasSize(2)
        assertThat(warns()[0]).contains("fast").contains("text").contains("bad/1")
        assertThat(warns()[1]).contains("fast").contains("image").contains("bad/2")
    }

    @Test
    fun `catalog not loaded at startup warns for every model as missing`() {
        validator(emptyMap(), catalogOf()).onReady()

        assertThat(warns()).hasSize(9)
    }

    @Test
    fun `a refresh re-validates against the new catalog`() {
        val v = validator(mapOf("balanced" to mapOf("text" to listOf("late/model"))))
        v.onReady()
        assertThat(warns()).hasSize(1)

        val refreshed = catalogOf(cm("ok/text"), cm("late/model"))
        val v2 = validator(mapOf("balanced" to mapOf("text" to listOf("late/model"))), refreshed)
        v2.onCatalogRefreshed(ModelCatalogRefreshed(Instant.now(), 2))
        // vision/image shipped models are absent from this catalog, so only they warn; late/model does not
        assertThat(warns().drop(1).joinToString()).doesNotContain("late/model")
    }

    @Test
    fun `warnings never contain the provider key`() {
        validator(mapOf("balanced" to mapOf("text" to listOf("nope/gone")))).onReady()

        assertThat(warns().joinToString()).doesNotContainIgnoringCase("api key").doesNotContain("sk-")
    }

    @Test
    fun `unknown profile key or slot warns at startup and does not throw`() {
        validator(mapOf("turbo" to mapOf("text" to listOf("x")), "fast" to mapOf("audio" to listOf("y")))).onReady()

        assertThat(warns().joinToString()).contains("turbo").contains("audio")
    }
}
