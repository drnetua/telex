package telex.agents.profile

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import telex.TestcontainersConfiguration
import telex.agents.ModelSlotKind
import telex.agents.SystemProfileKey
import telex.agents.internal.profile.OverrideValidator
import telex.agents.internal.profile.SystemProfiles
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.Modality
import telex.llm.ModelCatalog
import telex.llm.ModelCatalogRefreshed
import telex.llm.ModelId
import java.time.Duration
import java.time.Instant

@SpringBootTest(
    properties = [
        "telex.models.system-profiles.balanced.text=it/ok,it/missing,it/vision-only,it/ok2",
        "telex.models.system-profiles.turbo.text=it/ok",
    ],
)
@Import(TestcontainersConfiguration::class, SystemProfilesIT.FakeCatalog::class)
class SystemProfilesIT {
    @TestConfiguration
    class FakeCatalog {
        @Bean
        @Primary
        fun fakeCatalog(): ModelCatalog {
            val text = setOf(Modality.TEXT)
            val models =
                listOf(
                    CatalogModel(ModelId("it/ok"), "ok", "p", text, text, null, null, null, null),
                    CatalogModel(ModelId("it/ok2"), "ok2", "p", text, text, null, null, null, null),
                    CatalogModel(
                        ModelId("it/vision-only"),
                        "v",
                        "p",
                        setOf(Modality.TEXT, Modality.IMAGE),
                        setOf(Modality.IMAGE),
                        null,
                        null,
                        null,
                        null,
                    ),
                ).associateBy { it.modelId }
            return object : ModelCatalog {
                override fun snapshot() = CatalogSnapshot(models, Instant.now(), null, CatalogState.CURRENT)

                override fun find(modelId: ModelId) = models[modelId]
            }
        }
    }

    @Autowired lateinit var systemProfiles: SystemProfiles

    @Autowired lateinit var publisher: ApplicationEventPublisher

    @Autowired lateinit var tx: PlatformTransactionManager

    @Autowired lateinit var validator: OverrideValidator

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

    @Test
    fun `shipped defaults give every system profile three slots and balanced text is exactly the override`() {
        val all = systemProfiles.all()
        assertThat(all.map { it.key }).containsExactly(
            SystemProfileKey.FAST,
            SystemProfileKey.BALANCED,
            SystemProfileKey.CAREFUL,
        )
        all.forEach { p ->
            assertThat(p.slots[ModelSlotKind.TEXT]).describedAs("${p.key} text").isNotEmpty()
            assertThat(p.slots[ModelSlotKind.VISION]).describedAs("${p.key} vision").isNotEmpty()
            assertThat(p.slots[ModelSlotKind.IMAGE]).describedAs("${p.key} image").isNotEmpty()
            assertThat(
                p.slots.values
                    .flatten()
                    .size,
            ).isLessThanOrEqualTo(9)
        }
        val balanced = systemProfiles.profile(SystemProfileKey.BALANCED)
        assertThat(balanced.slots[ModelSlotKind.TEXT])
            .containsExactly(ModelId("it/ok"), ModelId("it/missing"), ModelId("it/vision-only"))
        assertThat(balanced.slots[ModelSlotKind.VISION]).isNotEqualTo(balanced.slots[ModelSlotKind.TEXT])
        assertThat(systemProfiles.unknownEntries.joinToString()).contains("turbo")
    }

    @Test
    fun `a published catalog refresh logs one WARN per bad override model`() {
        TransactionTemplate(tx).executeWithoutResult {
            publisher.publishEvent(ModelCatalogRefreshed(Instant.now(), 3))
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val warns = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
            assertThat(warns.filter { "it/missing" in it }).hasSize(1)
            assertThat(warns.filter { "it/vision-only" in it }).hasSize(1)
            assertThat(warns.filter { "it/ok2" in it }).hasSize(1)
            assertThat(warns.first { "it/missing" in it }).contains("balanced").contains("text")
        }
    }
}
