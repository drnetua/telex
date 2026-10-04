package telex.agents.internal.profile

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.identity.OwnerId
import telex.llm.ModelId
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Custom Model Profiles and their chains. Every query is scoped by `owner_id` (AC-222). */
@Repository
class ProfileRepository(
    private val jdbc: JdbcClient,
) {
    fun find(
        owner: OwnerId,
        id: ModelProfileId,
    ): ModelProfile? {
        val head =
            jdbc
                .sql("SELECT id, name FROM model_profile WHERE owner_id = :owner AND id = :id")
                .param("owner", owner.value)
                .param("id", id.value)
                .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("name") }
                .list()
                .singleOrNull() ?: return null
        return withChains(owner, listOf(head)).single()
    }

    fun list(owner: OwnerId): List<ModelProfile> {
        val heads =
            jdbc
                .sql("SELECT id, name FROM model_profile WHERE owner_id = :owner ORDER BY created_at, id")
                .param("owner", owner.value)
                .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("name") }
                .list()
        return withChains(owner, heads)
    }

    fun count(owner: OwnerId): Int =
        jdbc
            .sql("SELECT count(*) FROM model_profile WHERE owner_id = :owner")
            .param("owner", owner.value)
            .query(Int::class.java)
            .single()

    fun names(owner: OwnerId): List<String> =
        jdbc
            .sql("SELECT name FROM model_profile WHERE owner_id = :owner")
            .param("owner", owner.value)
            .query(String::class.java)
            .list()
            .filterNotNull()

    @Transactional
    fun insert(
        owner: OwnerId,
        profile: ModelProfile,
        createdAt: Instant,
    ) {
        jdbc
            .sql("INSERT INTO model_profile (id, owner_id, name, created_at) VALUES (:id, :owner, :name, :at)")
            .param("id", profile.id.value)
            .param("owner", owner.value)
            .param("name", profile.name)
            .param("at", Timestamp.from(createdAt))
            .update()
        insertChains(profile)
    }

    @Transactional
    fun update(
        owner: OwnerId,
        profile: ModelProfile,
    ): Boolean {
        val renamed =
            jdbc
                .sql("UPDATE model_profile SET name = :name WHERE owner_id = :owner AND id = :id")
                .param("name", profile.name)
                .param("owner", owner.value)
                .param("id", profile.id.value)
                .update()
        if (renamed == 0) return false
        jdbc
            .sql("DELETE FROM model_profile_slot_model WHERE model_profile_id = :id")
            .param("id", profile.id.value)
            .update()
        insertChains(profile)
        return true
    }

    fun delete(
        owner: OwnerId,
        id: ModelProfileId,
    ): Boolean =
        jdbc
            .sql("DELETE FROM model_profile WHERE owner_id = :owner AND id = :id")
            .param("owner", owner.value)
            .param("id", id.value)
            .update() > 0

    /** Serializes the Owner's saves (AC-218); must run inside the caller's transaction. */
    fun lockOwner(owner: OwnerId) {
        jdbc
            .sql("SELECT pg_advisory_xact_lock(hashtext(:owner))")
            .param("owner", owner.value.toString())
            .query { _, _ -> }
            .list()
    }

    private fun insertChains(profile: ModelProfile) {
        profile.slots.forEach { (kind, chain) ->
            chain.forEachIndexed { index, model ->
                jdbc
                    .sql(
                        "INSERT INTO model_profile_slot_model (model_profile_id, slot, position, model_id) " +
                            "VALUES (:id, :slot, :position, :model)",
                    ).param("id", profile.id.value)
                    .param("slot", kind.wire)
                    .param("position", index + 1)
                    .param("model", model.value)
                    .update()
            }
        }
    }

    private fun withChains(
        owner: OwnerId,
        heads: List<Pair<UUID, String>>,
    ): List<ModelProfile> {
        if (heads.isEmpty()) return emptyList()
        val ids = heads.map { it.first }
        // join back to model_profile so the chain read is owner-scoped too
        val rows =
            jdbc
                .sql(
                    "SELECT s.model_profile_id, s.slot, s.model_id FROM model_profile_slot_model s " +
                        "JOIN model_profile p ON p.id = s.model_profile_id " +
                        "WHERE p.owner_id = :owner AND s.model_profile_id = ANY(:ids) " +
                        "ORDER BY s.model_profile_id, s.slot, s.position",
                ).param("owner", owner.value)
                .param("ids", ids.toTypedArray())
                .query { rs, _ ->
                    Triple(rs.getObject(1, UUID::class.java), rs.getString(2), ModelId(rs.getString(3)))
                }.list()
        val byProfile = rows.groupBy { it.first }
        return heads.map { (id, name) ->
            val chains = byProfile[id].orEmpty().groupBy({ it.second }, { it.third })
            ModelProfile(
                ModelProfileId(id),
                name,
                ModelSlotKind.entries.associateWith { chains[it.wire].orEmpty() },
            )
        }
    }
}
