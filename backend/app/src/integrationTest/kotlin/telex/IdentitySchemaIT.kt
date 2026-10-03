package telex

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * The identity tables behind AC-34 (one Owner per canonical email), AC-85 (5 wrong attempts)
 * and AC-103 (one live grant).
 */
@Testcontainers
class IdentitySchemaIT {
    @Test
    fun `identity tables and indexes exist after migrating`() {
        val tables = query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        assertThat(tables).contains("owner", "sign_in_grant", "sign_in_session", "user_entities", "user_credentials")
        val indexes = query("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
        assertThat(indexes).contains(
            "owner_canonical_email_uq",
            "sign_in_grant_live_by_canonical_email_idx",
            "sign_in_session_owner_id_idx",
        )
    }

    @Test
    fun `a second owner with the same canonical email is rejected`() {
        exec("INSERT INTO owner VALUES (gen_random_uuid(), 'Anton+work@Mail.com', 'anton@mail.com', now())")
        assertThatThrownBy {
            exec("INSERT INTO owner VALUES (gen_random_uuid(), 'anton@mail.com', 'anton@mail.com', now())")
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("owner_canonical_email_uq")
    }

    @Test
    fun `a sixth wrong attempt is rejected by the grant constraint`() {
        assertThatThrownBy {
            exec(
                "INSERT INTO sign_in_grant (id, email, canonical_email, link_token_hash, code_hash, wrong_attempts, " +
                    "issued_at, expires_at) VALUES (gen_random_uuid(), 'a@b.c', 'a@b.c', " +
                    "decode(repeat('01', 32), 'hex'), decode(repeat('02', 32), 'hex'), 6, now(), " +
                    "now() + interval '1 hour')",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("sign_in_grant_wrong_attempts_ck")
    }

    @Test
    fun `owner preferences default for existing owners and reject bad rows`() {
        val pick = "WHERE email = 'pref@mail.com'"
        exec("INSERT INTO owner VALUES (gen_random_uuid(), 'pref@mail.com', 'pref@mail.com', now())")
        val stored = "theme || '/' || coalesce(time_zone, 'null') || '/' || time_zone_is_fallback"
        assertThat(query("SELECT $stored FROM owner $pick")).containsExactly("system/null/false")
        assertThatThrownBy { exec("UPDATE owner SET theme = 'blue' $pick") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("owner_theme_ck")
        assertThatThrownBy {
            exec("UPDATE owner SET time_zone = 'Europe/Kyiv', time_zone_is_fallback = true $pick")
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("owner_time_zone_fallback_ck")
        exec("UPDATE owner SET time_zone = 'UTC', time_zone_is_fallback = true, theme = 'dark' $pick")
    }

    private fun connect(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun exec(sql: String) = connect().use { c -> c.createStatement().use { it.execute(sql) } }

    private fun query(sql: String): List<String> =
        connect().use { c ->
            c.createStatement().use { s ->
                s.executeQuery(sql).use { r -> generateSequence { if (r.next()) r.getString(1) else null }.toList() }
            }
        }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(TestcontainersConfiguration.PGVECTOR_IMAGE)

        @BeforeAll
        @JvmStatic
        fun migrate() {
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }
}
