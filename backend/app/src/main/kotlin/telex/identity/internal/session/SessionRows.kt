package telex.identity.internal.session

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.OwnerId
import telex.identity.SignInSessionId
import telex.identity.internal.device.DeviceLabel
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class SessionRow(
    val id: SignInSessionId,
    val ownerId: OwnerId,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val endedAt: Instant?,
)

data class ListedRow(
    val id: SignInSessionId,
    val userAgentLabel: String,
    val deviceType: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
)

@Repository
class SessionRows(
    private val jdbc: JdbcClient,
) {
    fun insert(
        id: SignInSessionId,
        ownerId: OwnerId,
        keyHash: ByteArray,
        device: DeviceLabel,
        timeZone: String,
        now: Instant,
    ) {
        val at = ts(now)
        jdbc
            .sql(
                "INSERT INTO sign_in_session (id, owner_id, key_hash, user_agent_label, device_type, time_zone, " +
                    "started_at, last_activity_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).params(id.value, ownerId.value, keyHash, device.label, device.deviceType, timeZone, at, at)
            .update()
    }

    fun findByKeyHash(keyHash: ByteArray): SessionRow? =
        jdbc
            .sql(
                "SELECT id, owner_id, started_at, last_activity_at, ended_at FROM sign_in_session WHERE key_hash = ?",
            ).param(keyHash)
            .query { rs, _ ->
                SessionRow(
                    SignInSessionId(rs.getObject("id", UUID::class.java)),
                    OwnerId(rs.getObject("owner_id", UUID::class.java)),
                    rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
                    rs.getObject("last_activity_at", OffsetDateTime::class.java).toInstant(),
                    rs.getObject("ended_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }.optional()
            .orElse(null)

    fun markEnded(
        id: SignInSessionId,
        now: Instant,
    ) {
        jdbc
            .sql("UPDATE sign_in_session SET ended_at = ? WHERE id = ? AND ended_at IS NULL")
            .params(ts(now), id.value)
            .update()
    }

    fun endByKeyHash(
        keyHash: ByteArray,
        now: Instant,
    ) {
        jdbc
            .sql("UPDATE sign_in_session SET ended_at = ? WHERE key_hash = ? AND ended_at IS NULL")
            .params(ts(now), keyHash)
            .update()
    }

    /** Conditional update: at most one write per [minGap]. */
    fun bumpActivity(
        id: SignInSessionId,
        now: Instant,
        minGap: Duration,
    ) {
        jdbc
            .sql("UPDATE sign_in_session SET last_activity_at = ? WHERE id = ? AND last_activity_at <= ?")
            .params(ts(now), id.value, ts(now.minus(minGap)))
            .update()
    }

    fun listLive(
        ownerId: OwnerId,
        idleCutoff: Instant,
        ageCutoff: Instant,
    ): List<ListedRow> =
        jdbc
            .sql(
                "SELECT id, user_agent_label, device_type, started_at, last_activity_at FROM sign_in_session " +
                    "WHERE owner_id = ? AND ended_at IS NULL AND last_activity_at > ? AND started_at > ? " +
                    "ORDER BY started_at DESC, id DESC",
            ).params(ownerId.value, ts(idleCutoff), ts(ageCutoff))
            .query { rs, _ ->
                ListedRow(
                    SignInSessionId(rs.getObject("id", UUID::class.java)),
                    rs.getString("user_agent_label"),
                    rs.getString("device_type"),
                    rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
                    rs.getObject("last_activity_at", OffsetDateTime::class.java).toInstant(),
                )
            }.list()

    /** Ends one live session of this Owner; false when it is not theirs, not live or absent. */
    fun endOwned(
        ownerId: OwnerId,
        id: SignInSessionId,
        now: Instant,
    ): Boolean =
        jdbc
            .sql("UPDATE sign_in_session SET ended_at = ? WHERE id = ? AND owner_id = ? AND ended_at IS NULL")
            .params(ts(now), id.value, ownerId.value)
            .update() == 1

    fun endOthers(
        ownerId: OwnerId,
        keep: SignInSessionId,
        now: Instant,
    ) {
        jdbc
            .sql("UPDATE sign_in_session SET ended_at = ? WHERE owner_id = ? AND id <> ? AND ended_at IS NULL")
            .params(ts(now), ownerId.value, keep.value)
            .update()
    }

    private fun ts(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
