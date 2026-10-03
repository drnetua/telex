package telex.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.llm.internal.catalog.CatalogHolder
import telex.llm.internal.catalog.CatalogSnapshotStore
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CatalogSnapshotStoreIT {
    @Autowired lateinit var store: CatalogSnapshotStore

    @Autowired lateinit var jdbc: JdbcTemplate

    private val t1 = Instant.parse("2026-10-03T10:00:00Z")
    private val t2 = Instant.parse("2026-10-03T11:00:00Z")

    private val gpt =
        CatalogModel(
            ModelId("openai/gpt-x"),
            "GPT X",
            "openai",
            setOf(Modality.TEXT, Modality.IMAGE),
            setOf(Modality.TEXT),
            BigDecimal("1.500000"),
            BigDecimal("6.000000"),
            null,
            128_000,
        )
    private val img =
        CatalogModel(
            ModelId("acme/painter"),
            "Painter",
            "acme",
            setOf(Modality.TEXT),
            setOf(Modality.IMAGE),
            null,
            null,
            BigDecimal("0.040000"),
            null,
        )

    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM model_catalog_entry")
        jdbc.update("DELETE FROM model_catalog_state")
    }

    @Test
    fun `first start with nothing stored gives an empty not-loaded snapshot`() {
        val holder = CatalogHolder(store).also { it.load() }
        val s = holder.snapshot()
        assertThat(s.models).isEmpty()
        assertThat(s.refreshedAt).isNull()
        assertThat(s.state).isEqualTo(CatalogState.NOT_LOADED)
    }

    @Test
    fun `replace then a restart loads the same snapshot`() {
        store.replace(listOf(gpt, img), t1)
        val restarted = CatalogHolder(store).also { it.load() } // a fresh holder = a restarted app
        val s = restarted.snapshot()
        assertThat(s.models).containsOnlyKeys(gpt.modelId, img.modelId)
        assertThat(s.models[gpt.modelId]).isEqualTo(gpt)
        assertThat(s.models[img.modelId]).isEqualTo(img)
        assertThat(s.refreshedAt).isEqualTo(t1)
        assertThat(s.failedAt).isNull()
        assertThat(s.state).isEqualTo(CatalogState.CURRENT)
        assertThat(restarted.find(img.modelId)).isEqualTo(img)
        assertThat(restarted.find(ModelId("none/none"))).isNull()
    }

    @Test
    fun `a second replace swaps every entry`() {
        store.replace(listOf(gpt, img), t1)
        store.replace(listOf(img), t2)
        val s = store.load()
        assertThat(s.models).containsOnlyKeys(img.modelId)
        assertThat(s.refreshedAt).isEqualTo(t2)
    }

    @Test
    fun `a failure after a success keeps the models and reports update-failed`() {
        store.replace(listOf(gpt), t1)
        store.recordFailure(t2)
        val s = CatalogHolder(store).also { it.load() }.snapshot()
        assertThat(s.models).containsOnlyKeys(gpt.modelId)
        assertThat(s.refreshedAt).isEqualTo(t1)
        assertThat(s.failedAt).isEqualTo(t2)
        assertThat(s.state).isEqualTo(CatalogState.UPDATE_FAILED)
    }

    @Test
    fun `a failure with no success ever reports not-loaded`() {
        store.recordFailure(t1)
        val s = CatalogHolder(store).also { it.load() }.snapshot()
        assertThat(s.models).isEmpty()
        assertThat(s.refreshedAt).isNull()
        assertThat(s.failedAt).isEqualTo(t1)
        assertThat(s.state).isEqualTo(CatalogState.NOT_LOADED)
    }

    @Test
    fun `a later successful replace after a failure is current again`() {
        store.replace(listOf(gpt), t1)
        store.recordFailure(t2)
        store.replace(listOf(gpt), Instant.parse("2026-10-03T12:00:00Z"))
        assertThat(store.load().state).isEqualTo(CatalogState.CURRENT)
    }

    @Test
    fun `a replace that fails half-way rolls back and keeps the previous snapshot`() {
        store.replace(listOf(gpt), t1)
        assertThatThrownBy { store.replace(listOf(img, img), t2) } // duplicate PK on the second insert
            .isInstanceOf(Exception::class.java)
        val s = store.load()
        assertThat(s.models).containsOnlyKeys(gpt.modelId)
        assertThat(s.refreshedAt).isEqualTo(t1)
    }
}
