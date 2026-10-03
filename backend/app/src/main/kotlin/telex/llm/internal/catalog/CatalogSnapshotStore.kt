package telex.llm.internal.catalog

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.Modality
import telex.llm.ModelId
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The last known Model Catalog in Postgres: one transaction per replace, so a restart never sees a half list. */
@Repository
class CatalogSnapshotStore(
    private val jdbc: JdbcClient,
) {
    @Transactional
    fun replace(
        models: List<CatalogModel>,
        refreshedAt: Instant,
    ) {
        jdbc.sql("DELETE FROM model_catalog_entry").update()
        models.forEach(::insert)
        jdbc
            .sql(
                """
                INSERT INTO model_catalog_state (id, last_refreshed_at) VALUES (1, :at)
                ON CONFLICT (id) DO UPDATE SET last_refreshed_at = EXCLUDED.last_refreshed_at
                """.trimIndent(),
            ).param("at", utc(refreshedAt))
            .update()
    }

    @Transactional
    fun recordFailure(at: Instant) {
        jdbc
            .sql(
                """
                INSERT INTO model_catalog_state (id, last_failed_at) VALUES (1, :at)
                ON CONFLICT (id) DO UPDATE SET last_failed_at = EXCLUDED.last_failed_at
                """.trimIndent(),
            ).param("at", utc(at))
            .update()
    }

    @Transactional(readOnly = true)
    fun load(): CatalogSnapshot {
        val models =
            jdbc
                .sql("SELECT * FROM model_catalog_entry")
                .query { rs, _ -> toModel(rs) }
                .list()
                .associateBy { it.modelId }
        val times =
            jdbc
                .sql("SELECT last_refreshed_at, last_failed_at FROM model_catalog_state WHERE id = 1")
                .query { rs, _ -> instant(rs, "last_refreshed_at") to instant(rs, "last_failed_at") }
                .optional()
                .orElse(null)
        val refreshedAt = times?.first
        val failedAt = times?.second
        val state =
            when {
                refreshedAt == null -> CatalogState.NOT_LOADED
                failedAt != null && failedAt > refreshedAt -> CatalogState.UPDATE_FAILED
                else -> CatalogState.CURRENT
            }
        return CatalogSnapshot(models, refreshedAt, failedAt, state)
    }

    private fun insert(m: CatalogModel) {
        jdbc
            .sql(
                """
                INSERT INTO model_catalog_entry (model_id, name, provider, takes_text, takes_images, produces_text,
                    produces_images, input_price_per_mtok, output_price_per_mtok, price_per_image, context_length)
                VALUES (:id, :name, :provider, :tt, :ti, :pt, :pi, :inp, :outp, :img, :ctx)
                """.trimIndent(),
            ).param("id", m.modelId.value)
            .param("name", m.name)
            .param("provider", m.provider)
            .param("tt", Modality.TEXT in m.takes)
            .param("ti", Modality.IMAGE in m.takes)
            .param("pt", Modality.TEXT in m.produces)
            .param("pi", Modality.IMAGE in m.produces)
            .param("inp", m.inputPerMtok)
            .param("outp", m.outputPerMtok)
            .param("img", m.perImage)
            .param("ctx", m.contextLength)
            .update()
    }

    private fun toModel(rs: ResultSet): CatalogModel =
        CatalogModel(
            modelId = ModelId(rs.getString("model_id")),
            name = rs.getString("name"),
            provider = rs.getString("provider"),
            takes = modalities(rs.getBoolean("takes_text"), rs.getBoolean("takes_images")),
            produces = modalities(rs.getBoolean("produces_text"), rs.getBoolean("produces_images")),
            inputPerMtok = rs.getBigDecimal("input_price_per_mtok"),
            outputPerMtok = rs.getBigDecimal("output_price_per_mtok"),
            perImage = rs.getBigDecimal("price_per_image"),
            contextLength = rs.getObject("context_length") as Int?,
        )

    private fun modalities(
        text: Boolean,
        image: Boolean,
    ): Set<Modality> =
        buildSet {
            if (text) add(Modality.TEXT)
            if (image) add(Modality.IMAGE)
        }

    private fun instant(
        rs: ResultSet,
        column: String,
    ): Instant? = rs.getObject(column, OffsetDateTime::class.java)?.toInstant()

    private fun utc(at: Instant): OffsetDateTime = at.atOffset(ZoneOffset.UTC)
}
