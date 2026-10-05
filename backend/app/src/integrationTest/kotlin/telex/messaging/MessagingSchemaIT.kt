package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import telex.TestcontainersConfiguration
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/**
 * The tables behind AC-04 / AC-108 (one Telegram account belongs to one Owner, matched by Telegram user id)
 * and AC-111 (unlinking deletes the chat list with the account).
 */
@Testcontainers
class MessagingSchemaIT {
    @Test
    fun `tables and indexes exist after migrating`() {
        val tables = query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        assertThat(tables).contains("owner_key", "linked_account", "channel")
        val indexes = query("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
        assertThat(indexes).contains(
            "linked_account_telegram_user_id_uq",
            "linked_account_telegram_session_id_uq",
            "linked_account_owner_id_idx",
            "channel_linked_account_telegram_chat_uq",
        )
    }

    @Test
    fun `a second linked account with the same telegram user id is rejected even for another owner`() {
        val first = owner()
        val second = owner()
        val telegramUserId = nextTelegramUserId()
        account(first, telegramUserId)
        assertThatThrownBy { account(second, telegramUserId) }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("linked_account_telegram_user_id_uq")
        assertThatThrownBy { account(first, telegramUserId) }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("linked_account_telegram_user_id_uq")
    }

    @Test
    fun `deleting a linked account deletes its channels`() {
        val owner = owner()
        val account = account(owner, nextTelegramUserId())
        channel(owner, account, 1)
        channel(owner, account, 2)
        assertThat(channelCount(account)).isEqualTo(2)
        exec("DELETE FROM linked_account WHERE id = '$account'")
        assertThat(channelCount(account)).isZero()
    }

    @Test
    fun `a channel carrying another owner id is rejected by the composite foreign key`() {
        val owner = owner()
        val other = owner()
        val account = account(owner, nextTelegramUserId())
        assertThatThrownBy { channel(other, account, 1) }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("channel_linked_account_fk")
    }

    @Test
    fun `a session without a sealed key and a connected account without a session are rejected`() {
        val owner = owner()
        assertThatThrownBy {
            exec(
                "INSERT INTO linked_account (id, owner_id, telegram_user_id, telegram_session_id, display_name, " +
                    "phone_country_code, phone_last_digits, state, created_at) VALUES (gen_random_uuid(), " +
                    "'$owner', ${nextTelegramUserId()}, gen_random_uuid(), 'A', '380', '42', 'connected', now())",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("linked_account_session_pair_ck")
        assertThatThrownBy {
            exec(
                "INSERT INTO linked_account (id, owner_id, telegram_user_id, display_name, phone_country_code, " +
                    "phone_last_digits, state, created_at) VALUES (gen_random_uuid(), '$owner', " +
                    "${nextTelegramUserId()}, 'A', '380', '42', 'connected', now())",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("linked_account_session_required_ck")
    }

    @Test
    fun `a session lost account may lack a session and an unknown state is rejected`() {
        val owner = owner()
        exec(
            "INSERT INTO linked_account (id, owner_id, telegram_user_id, display_name, phone_country_code, " +
                "phone_last_digits, state, created_at) VALUES (gen_random_uuid(), '$owner', " +
                "${nextTelegramUserId()}, 'A', '380', '42', 'session_lost', now())",
        )
        assertThatThrownBy { account(owner, nextTelegramUserId(), state = "gone") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("linked_account_state_ck")
    }

    @Test
    fun `the sealed owner key must be 60 bytes`() {
        val owner = owner()
        assertThatThrownBy {
            exec("INSERT INTO owner_key VALUES ('$owner', decode(repeat('01', 59), 'hex'), now())")
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("owner_key_sealed_key_len_ck")
        exec("INSERT INTO owner_key VALUES ('$owner', decode(repeat('01', 60), 'hex'), now())")
    }

    private fun owner(): UUID {
        val id = UUID.randomUUID()
        exec("INSERT INTO owner VALUES ('$id', '$id@mail.com', '$id@mail.com', now())")
        return id
    }

    private fun account(
        owner: UUID,
        telegramUserId: Long,
        state: String = "connected",
    ): UUID {
        val id = UUID.randomUUID()
        exec(
            "INSERT INTO linked_account (id, owner_id, telegram_user_id, telegram_session_id, tdlib_key_sealed, " +
                "display_name, phone_country_code, phone_last_digits, state, created_at) VALUES ('$id', '$owner', " +
                "$telegramUserId, gen_random_uuid(), decode(repeat('01', 60), 'hex'), 'A', '380', '42', " +
                "'$state', now())",
        )
        return id
    }

    private fun channel(
        owner: UUID,
        account: UUID,
        chatId: Long,
    ) = exec(
        "INSERT INTO channel VALUES (gen_random_uuid(), '$owner', '$account', $chatId, 'private', 'Chat', " +
            "'{}', false, 0, 0)",
    )

    private fun channelCount(account: UUID) =
        query("SELECT count(*) FROM channel WHERE linked_account_id = '$account'").single().toInt()

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

        private var telegramUserSeq = 1000L

        private fun nextTelegramUserId() = telegramUserSeq++

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
