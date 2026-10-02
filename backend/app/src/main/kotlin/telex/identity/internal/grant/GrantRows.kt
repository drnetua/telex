package telex.identity.internal.grant

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.SignInGrantId
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

    fun findByLinkHash(linkTokenHash: ByteArray): GrantRow? =
        jdbc
            .sql(
                "SELECT id, email, canonical_email, wrong_attempts, expires_at, used_at, superseded_at " +
                    "FROM sign_in_grant WHERE link_token_hash = ?",
            ).param(linkTokenHash)
            .query { rs, _ ->
                GrantRow(
                    SignInGrantId(rs.getObject("id", UUID::class.java)),
                    rs.getString("email"),
                    rs.getString("canonical_email"),
                    rs.getInt("wrong_attempts"),
                    rs.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
                    rs.getObject("used_at", OffsetDateTime::class.java)?.toInstant(),
                    rs.getObject("superseded_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }.optional()
            .orElse(null)

    private fun ts(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
