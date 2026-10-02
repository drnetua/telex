package telex.identity.internal.grant

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.SignInGrantId
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class GrantRow(
    val id: SignInGrantId,
    val email: String,
    val canonicalEmail: String,
    val wrongAttempts: Int,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val supersededAt: Instant?,
)

@Repository
class GrantRows(
    private val jdbc: JdbcClient,
) {
    fun supersedeLive(
        canonicalEmail: String,
        now: Instant,
    ) {
        // Serialise issuing per address until commit, so at most one grant stays live (AC-103).
        jdbc
            .sql("SELECT pg_advisory_xact_lock(hashtext(?))")
            .params(canonicalEmail)
            .query()
            .singleRow()
        jdbc
            .sql(
                "UPDATE sign_in_grant SET superseded_at = ? " +
                    "WHERE canonical_email = ? AND used_at IS NULL AND superseded_at IS NULL",
            ).params(ts(now), canonicalEmail)
            .update()
    }

    @Suppress("LongParameterList")
    fun insert(
        id: SignInGrantId,
        email: String,
        canonicalEmail: String,
        linkTokenHash: ByteArray,
        codeHash: ByteArray,
        issuedAt: Instant,
        expiresAt: Instant,
    ) {
        jdbc
            .sql(
                "INSERT INTO sign_in_grant (id, email, canonical_email, link_token_hash, code_hash, " +
                    "issued_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).params(id.value, email, canonicalEmail, linkTokenHash, codeHash, ts(issuedAt), ts(expiresAt))
            .update()
    }

    fun findByLinkHash(linkTokenHash: ByteArray): GrantRow? = find("link_token_hash = ?", linkTokenHash)

    fun findById(id: SignInGrantId): GrantRow? = find("id = ?", id.value)

    /** Atomically marks a usable grant used, by its link; null when no usable grant matches (ADR-0003). */
    fun redeemByLink(
        linkTokenHash: ByteArray,
        now: Instant,
    ): GrantRow? = redeem("link_token_hash = ?", linkTokenHash, now)

    /** Atomically marks a usable grant used, by its id and code hash. */
    fun redeemByCode(
        id: SignInGrantId,
        codeHash: ByteArray,
        now: Instant,
    ): GrantRow? =
        jdbc
            .sql(
                "UPDATE sign_in_grant SET used_at = ? WHERE id = ? AND code_hash = ? AND $USABLE " +
                    "RETURNING $COLUMNS",
            ).params(ts(now), id.value, codeHash, ts(now))
            .query(::row)
            .optional()
            .orElse(null)

    /** Adds one wrong attempt to a usable grant; returns the new count, or null when the grant is not usable. */
    fun addWrongAttempt(
        id: SignInGrantId,
        now: Instant,
    ): Int? =
        jdbc
            .sql(
                "UPDATE sign_in_grant SET wrong_attempts = wrong_attempts + 1 WHERE id = ? AND $USABLE " +
                    "RETURNING wrong_attempts",
            ).params(id.value, ts(now))
            .query(Int::class.java)
            .optional()
            .orElse(null)

    private fun redeem(
        key: String,
        value: Any,
        now: Instant,
    ): GrantRow? =
        jdbc
            .sql("UPDATE sign_in_grant SET used_at = ? WHERE $key AND $USABLE RETURNING $COLUMNS")
            .params(ts(now), value, ts(now))
            .query(::row)
            .optional()
            .orElse(null)

    private fun find(
        where: String,
        value: Any,
    ): GrantRow? =
        jdbc
            .sql("SELECT $COLUMNS FROM sign_in_grant WHERE $where")
            .param(value)
            .query(::row)
            .optional()
            .orElse(null)

    private fun row(
        rs: ResultSet,
        @Suppress("UNUSED_PARAMETER") n: Int,
    ) = GrantRow(
        SignInGrantId(rs.getObject("id", UUID::class.java)),
        rs.getString("email"),
        rs.getString("canonical_email"),
        rs.getInt("wrong_attempts"),
        rs.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
        rs.getObject("used_at", OffsetDateTime::class.java)?.toInstant(),
        rs.getObject("superseded_at", OffsetDateTime::class.java)?.toInstant(),
    )

    private companion object {
        const val COLUMNS = "id, email, canonical_email, wrong_attempts, expires_at, used_at, superseded_at"
        const val USABLE =
            "used_at IS NULL AND superseded_at IS NULL AND wrong_attempts < 5 AND expires_at > ?"
    }

    private fun ts(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
