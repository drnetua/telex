package telex.agents.internal.profile

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.agents.ModelProfileId
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.identity.OwnerId
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** The Owner's default profile. No row means Balanced (ADR-0005). */
@Repository
class DefaultProfileRepository(
    private val jdbc: JdbcClient,
) {
    fun get(owner: OwnerId): ProfileRef? =
        jdbc
            .sql("SELECT system_profile_key, custom_profile_id FROM default_model_profile WHERE owner_id = :owner")
            .param("owner", owner.value)
            .query { rs, _ ->
                val custom = rs.getObject("custom_profile_id", UUID::class.java)
                if (custom != null) {
                    ProfileRef.Custom(ModelProfileId(custom))
                } else {
                    val key = rs.getString("system_profile_key")
                    ProfileRef.System(SystemProfileKey.entries.first { it.wire == key })
                }
            }.list()
            .singleOrNull()
            ?.takeUnless { it == BALANCED }

    fun set(
        owner: OwnerId,
        ref: ProfileRef,
        at: Instant,
    ) {
        if (ref == BALANCED) {
            jdbc
                .sql("DELETE FROM default_model_profile WHERE owner_id = :owner")
                .param("owner", owner.value)
                .update()
            return
        }
        jdbc
            .sql(
                "INSERT INTO default_model_profile (owner_id, system_profile_key, custom_profile_id, chosen_at) " +
                    "VALUES (:owner, :key, :custom, :at) " +
                    "ON CONFLICT (owner_id) DO UPDATE SET system_profile_key = EXCLUDED.system_profile_key, " +
                    "custom_profile_id = EXCLUDED.custom_profile_id, chosen_at = EXCLUDED.chosen_at",
            ).param("owner", owner.value)
            .param("key", (ref as? ProfileRef.System)?.key?.wire, java.sql.Types.VARCHAR)
            .param("custom", (ref as? ProfileRef.Custom)?.id?.value, java.sql.Types.OTHER)
            .param("at", Timestamp.from(at))
            .update()
    }

    /** True when the default pointed at this custom profile (the row is removed, so it is Balanced again). */
    fun clearIfCustom(
        owner: OwnerId,
        id: ModelProfileId,
    ): Boolean =
        jdbc
            .sql("DELETE FROM default_model_profile WHERE owner_id = :owner AND custom_profile_id = :id")
            .param("owner", owner.value)
            .param("id", id.value)
            .update() > 0

    private companion object {
        val BALANCED = ProfileRef.System(SystemProfileKey.BALANCED)
    }
}
