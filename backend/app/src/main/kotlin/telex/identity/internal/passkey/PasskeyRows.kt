package telex.identity.internal.passkey

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

data class PasskeyRow(
    val id: String,
    val label: String,
    val createdAt: Instant?,
    val lastUsedAt: Instant?,
)

/** Owner-scoped reads and deletes over `user_credentials`, joined through the Owner's `user_entities` row. */
@Repository
class PasskeyRows(
    private val jdbc: JdbcClient,
) {
    fun listFor(ownerName: String): List<PasskeyRow> =
        jdbc
            .sql(
                """
                SELECT c.credential_id, c.label, c.created, c.last_used
                FROM user_credentials c JOIN user_entities e ON e.id = c.user_entity_user_id
                WHERE e.name = :name
                ORDER BY c.created DESC NULLS LAST, c.credential_id
                """.trimIndent(),
            ).param("name", ownerName)
            .query { rs, _ ->
                PasskeyRow(
                    rs.getString("credential_id"),
                    rs.getString("label"),
                    rs.getTimestamp("created")?.toInstant(),
                    rs.getTimestamp("last_used")?.toInstant(),
                )
            }.list()

    fun deleteFor(
        ownerName: String,
        credentialId: String,
    ): Boolean =
        jdbc
            .sql(
                """
                DELETE FROM user_credentials
                WHERE credential_id = :id
                  AND user_entity_user_id IN (SELECT id FROM user_entities WHERE name = :name)
                """.trimIndent(),
            ).param("id", credentialId)
            .param("name", ownerName)
            .update() > 0

    /** Creates the user entity unless the Owner already has one, so concurrent first registrations both succeed. */
    fun insertUserEntityIfAbsent(
        id: String,
        ownerName: String,
        displayName: String,
    ) {
        jdbc
            .sql(
                "INSERT INTO user_entities (id, name, display_name) VALUES (:id, :name, :displayName) " +
                    "ON CONFLICT (name) DO NOTHING",
            ).param("id", id)
            .param("name", ownerName)
            .param("displayName", displayName)
            .update()
    }
}
