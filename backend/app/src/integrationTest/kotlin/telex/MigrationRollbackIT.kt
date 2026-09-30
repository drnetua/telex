package telex

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * Every Flyway migration `db/migration/V<v>__<name>.sql` must have a rollback `db/rollback/U<v>__<name>.sql`
 * that restores the schema exactly: up -> down (newest first) -> up.
 */
@Testcontainers
class MigrationRollbackIT {
    @Test
    fun `every migration applies, rolls back and re-applies`() {
        val flyway =
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
        val empty = schemaSnapshot()

        assertThat(flyway.migrate().migrationsExecuted).isPositive()
        val migrated = schemaSnapshot()

        flyway.info().applied().reversed().forEach { migration ->
            val rollback = ClassPathResource("db/rollback/${migration.script.replaceFirst("V", "U")}")
            assertThat(rollback.exists()).describedAs("rollback for ${migration.script}").isTrue()
            connect().use { connection ->
                connection.createStatement().use { it.execute(rollback.getContentAsString(Charsets.UTF_8)) }
                connection.prepareStatement("DELETE FROM flyway_schema_history WHERE version = ?").use {
                    it.setString(1, migration.version.version)
                    it.executeUpdate()
                }
            }
        }
        assertThat(schemaSnapshot()).isEqualTo(empty)

        flyway.migrate()
        assertThat(schemaSnapshot()).isEqualTo(migrated)
    }

    private fun connect(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    /** Extensions, tables, columns and indexes of the `public` schema, without Flyway's own history table. */
    private fun schemaSnapshot(): Set<String> =
        connect().use { connection ->
            SNAPSHOT_QUERIES.flatMapTo(sortedSetOf()) { query ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(query).use { rows ->
                        generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
                    }
                }
            }
        }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(TestcontainersConfiguration.PGVECTOR_IMAGE)

        private val SNAPSHOT_QUERIES =
            listOf(
                "SELECT 'extension ' || extname FROM pg_extension",
                """
                SELECT 'column ' || table_name || '.' || column_name || ' ' || data_type || ' ' || is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """,
                """
                SELECT 'index ' || indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                """,
            )
    }
}
