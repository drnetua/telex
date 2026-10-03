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
import java.util.UUID

/**
 * The model-profiles tables behind AC-212 (catalog state survives a restart), AC-217 (a slot holds at most three
 * models, each once), AC-220 (deleting a profile drops its chain and default) and AC-229 (call records outlive it).
 */
@Testcontainers
class ModelProfilesSchemaIT {
    @Test
    fun `model profile tables exist after migrating`() {
        val tables = query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        assertThat(tables).contains(
            "model_catalog_entry",
            "model_catalog_state",
            "model_profile",
            "model_profile_slot_model",
            "default_model_profile",
            "model_call",
            "model_call_attempt",
        )
    }

    @Test
    fun `a fourth model in one slot is rejected`() {
        val profile = profile(owner())
        (1..3).forEach { chain(profile, "text", it, "m$it") }
        assertThatThrownBy { chain(profile, "text", 4, "m4") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("model_profile_slot_model_position_ck")
    }

    @Test
    fun `the same model twice in one slot is rejected`() {
        val profile = profile(owner())
        chain(profile, "text", 1, "m1")
        assertThatThrownBy { chain(profile, "text", 2, "m1") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("model_profile_slot_model_once_uq")
    }

    @Test
    fun `a default with both references set is rejected`() {
        val owner = owner()
        val profile = profile(owner)
        assertThatThrownBy {
            exec(
                "INSERT INTO default_model_profile (owner_id, system_profile_key, custom_profile_id, chosen_at) " +
                    "VALUES ('$owner', 'fast', '$profile', now())",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("default_model_profile_ref_ck")
    }

    @Test
    fun `deleting a profile removes its chain and default but keeps its call records`() {
        val owner = owner()
        val profile = profile(owner)
        chain(profile, "text", 1, "m1")
        exec(
            "INSERT INTO default_model_profile (owner_id, custom_profile_id, chosen_at) " +
                "VALUES ('$owner', '$profile', now())",
        )
        exec(
            "INSERT INTO model_call (id, owner_id, custom_profile_id, slot, outcome, answered_by_model_id, " +
                "fallback, started_at, finished_at) VALUES (gen_random_uuid(), '$owner', '$profile', 'text', " +
                "'answered', 'm1', false, now(), now())",
        )

        exec("DELETE FROM model_profile WHERE id = '$profile'")

        assertThat(count("model_profile_slot_model WHERE model_profile_id = '$profile'")).isZero()
        assertThat(count("default_model_profile WHERE owner_id = '$owner'")).isZero()
        assertThat(count("model_call WHERE custom_profile_id = '$profile'")).isEqualTo(1)
    }

    @Test
    fun `the catalog state is a singleton row`() {
        assertThatThrownBy {
            exec("INSERT INTO model_catalog_state (id, last_refreshed_at) VALUES (2, now())")
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("model_catalog_state_single_row_ck")
    }

    private fun owner(): UUID {
        val id = UUID.randomUUID()
        exec("INSERT INTO owner VALUES ('$id', '$id@mail.com', '$id@mail.com', now())")
        return id
    }

    private fun profile(owner: UUID): UUID {
        val id = UUID.randomUUID()
        exec("INSERT INTO model_profile (id, owner_id, name, created_at) VALUES ('$id', '$owner', 'P-$id', now())")
        return id
    }

    private fun chain(
        profile: UUID,
        slot: String,
        position: Int,
        model: String,
    ) = exec(
        "INSERT INTO model_profile_slot_model (model_profile_id, slot, position, model_id) " +
            "VALUES ('$profile', '$slot', $position, '$model')",
    )

    private fun count(fromWhere: String): Int = query("SELECT count(*) FROM $fromWhere").single().toInt()

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
