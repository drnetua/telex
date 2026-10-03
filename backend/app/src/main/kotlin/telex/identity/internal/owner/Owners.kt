package telex.identity.internal.owner

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.OwnerId
import telex.identity.Preferences
import telex.identity.Theme
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

    fun emailOf(id: OwnerId): String? =
        jdbc
            .sql("SELECT email FROM owner WHERE id = ?")
            .param(id.value)
            .query(String::class.java)
            .optional()
            .orElse(null)

    fun preferencesOf(id: OwnerId): Preferences? = emailAndPreferencesOf(id)?.second

    /** The address and the preferences in one `SELECT`, for `GET /me`. */
    fun emailAndPreferencesOf(id: OwnerId): Pair<String, Preferences>? =
        jdbc
            .sql("SELECT email, theme, time_zone, time_zone_is_fallback FROM owner WHERE id = ?")
            .param(id.value)
            .query { rs, _ ->
                rs.getString("email") to
                    Preferences(
                        checkNotNull(Theme.fromWire(rs.getString("theme"))),
                        rs.getString("time_zone"),
                        rs.getBoolean("time_zone_is_fallback"),
                    )
            }.optional()
            .orElse(null)

    fun updateTheme(
        id: OwnerId,
        theme: String,
    ) {
        jdbc.sql("UPDATE owner SET theme = ? WHERE id = ?").params(theme, id.value).update()
    }

    fun updateTimeZone(
        id: OwnerId,
        zone: String,
    ) {
        jdbc
            .sql("UPDATE owner SET time_zone = ?, time_zone_is_fallback = false WHERE id = ?")
            .params(zone, id.value)
            .update()
    }

    /** Returns rows affected; zero means a zone was already saved. */
    fun saveTimeZoneIfUnset(
        id: OwnerId,
        zone: String,
        isFallback: Boolean,
    ): Int =
        jdbc
            .sql("UPDATE owner SET time_zone = ?, time_zone_is_fallback = ? WHERE id = ? AND time_zone IS NULL")
            .params(zone, isFallback, id.value)
            .update()
}
