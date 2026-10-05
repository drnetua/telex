package telex.identity.internal.key

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.OwnerId
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class OwnerKeyRows(
    private val jdbc: JdbcClient,
) {
    fun find(ownerId: OwnerId): ByteArray? =
        jdbc
            .sql("SELECT sealed_key FROM owner_key WHERE owner_id = ?")
            .param(ownerId.value)
            .query(ByteArray::class.java)
            .optional()
            .orElse(null)

    /** Inserts unless the Owner already has a key (a concurrent creator wins). */
    fun insertIfAbsent(
        ownerId: OwnerId,
        sealed: ByteArray,
        now: Instant,
    ) {
        jdbc
            .sql(
                "INSERT INTO owner_key (owner_id, sealed_key, created_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT (owner_id) DO NOTHING",
            ).params(ownerId.value, sealed, OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .update()
    }

    fun any(): Pair<OwnerId, ByteArray>? =
        jdbc
            .sql("SELECT owner_id, sealed_key FROM owner_key LIMIT 1")
            .query { rs, _ ->
                OwnerId(rs.getObject("owner_id", UUID::class.java)) to rs.getBytes("sealed_key")
            }.optional()
            .orElse(null)

    fun deleteAll() {
        jdbc.sql("DELETE FROM owner_key").update()
    }
}
