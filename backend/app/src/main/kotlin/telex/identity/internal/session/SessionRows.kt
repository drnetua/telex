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

    private fun ts(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
