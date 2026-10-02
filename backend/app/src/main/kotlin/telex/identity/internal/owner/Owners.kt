package telex.identity.internal.owner

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.OwnerId
import telex.shared.Uuid7
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class Owners(
    private val jdbc: JdbcClient,
) {
    /** Find by canonical address or create it; concurrent first sign-ins resolve to one row. */
    fun findOrCreate(
        emailAsTyped: String,
        canonical: String,
        now: Instant,
    ): Pair<OwnerId, Boolean> {
        val inserted =
            jdbc
                .sql(
                    "INSERT INTO owner (id, email, canonical_email, created_at) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (canonical_email) DO NOTHING",
                ).params(Uuid7.next(), emailAsTyped, canonical, OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update()
        val id =
            jdbc
                .sql("SELECT id FROM owner WHERE canonical_email = ?")
                .param(canonical)
                .query(UUID::class.java)
                .single()
        return OwnerId(id) to (inserted == 1)
    }
}
