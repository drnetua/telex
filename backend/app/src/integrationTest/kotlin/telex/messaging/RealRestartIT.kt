package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.testcontainers.postgresql.PostgreSQLContainer
import telex.TelexApplication
import telex.TestcontainersConfiguration
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.shared.Uuid7
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private const val KEY_BYTES = 32

/**
 * AC-36 and AC-112 across a real restart: the first Spring context is closed and a second one is started on the
 * same database and session directory, with no `simulateStop`, no manual `BootReconnect.run` and no manual
 * republish. The in-memory Telegram forgets its clients with the first context, as a real process would.
 */
class RealRestartIT {
    /** State lives outside the listener bean, which Spring proxies for its transaction. */
    object Delivered {
        val received = CopyOnWriteArrayList<AccountUnlinked>()
        val down = AtomicBoolean(true)
    }

    open class Listener {
        @ApplicationModuleListener
        open fun on(event: AccountUnlinked) {
            check(!Delivered.down.get()) { "the process stopped before delivery" }
            Delivered.received += event
        }
    }

    @TestConfiguration
    class Probe {
        @Bean
        fun listener() = Listener()
    }

    private class Started(
        val context: ConfigurableApplicationContext,
    ) {
        inline fun <reified T : Any> bean(): T = context.getBean(T::class.java)
    }

    private fun start(): Started =
        Started(
            SpringApplicationBuilder(TelexApplication::class.java, Probe::class.java)
                .run(
                    "--spring.datasource.url=${postgres.jdbcUrl}",
                    "--spring.datasource.username=${postgres.username}",
                    "--spring.datasource.password=${postgres.password}",
                    "--server.port=0",
                    "--spring.jmx.enabled=false",
                    "--telex.telegram.adapter=fake",
                    "--telex.telegram.sessions-dir=$sessions",
                    "--telex.telegram.api-id=54321",
                    "--telex.telegram.api-hash=abcdef",
                    "--telex.master-key=" + Base64.getEncoder().encodeToString(ByteArray(KEY_BYTES) { 5 }),
                ),
        )

    private fun connected(
        app: Started,
        owner: OwnerId,
        phone: String,
    ): Pair<LinkedAccountId, TelegramSessionId> {
        val telegram = app.bean<TelegramSessions>()
        val key = ByteArray(KEY_BYTES) { 7 }
        val session = telegram.open(key)
        telegram.sendPhone(session, phone)
        check(telegram.checkCode(session, FakeTelegram.CODE) is SignInOutcome.Authorized)
        val id = LinkedAccountId(Uuid7.next())
        app.bean<LinkedAccountRows>().insert(
            NewLinkedAccount(
                id,
                owner,
                phone.toLong(),
                session,
                app.bean<OwnerKeys>().seal(owner, key, id.keyAad()),
                "Anna",
                MaskedPhone("999", "00"),
                Instant.now(),
            ),
        )
        return id to session
    }

    @Test
    fun `AC-36 AC-112 a closed and restarted app reconnects its accounts and delivers the unlink it never announced`() {
        Delivered.received.clear()
        Delivered.down.set(true)
        val owner = OwnerId(UUID.randomUUID())
        val kept: Pair<LinkedAccountId, TelegramSessionId>
        val unlinked: LinkedAccountId
        val first = start()
        try {
            val jdbc = first.bean<JdbcTemplate>()
            jdbc.update(
                "INSERT INTO owner VALUES (?, ?, ?, now())",
                owner.value,
                "restart@mail.com",
                "restart@mail.com",
            )
            kept = connected(first, owner, "9996600701")
            unlinked = connected(first, owner, "9996600702").first
            // the process dies while the account was mid-reconnect
            jdbc.update("UPDATE linked_account SET state = 'reconnecting' WHERE id = ?", kept.first.value)
            assertThat(first.bean<AccountDeletion>().delete(owner, unlinked)).isNotNull
            await().atMost(Duration.ofSeconds(5)).until { outstanding(jdbc) >= 1 }
        } finally {
            first.context.close()
        }
        assertThat(Delivered.received).isEmpty()

        Delivered.down.set(false)
        val second = start()
        try {
            val jdbc = second.bean<JdbcTemplate>()
            val fake = second.bean<TelegramSessions>() as FakeTelegram

            await().atMost(Duration.ofSeconds(30)).untilAsserted {
                assertThat(
                    jdbc.queryForObject(
                        "SELECT state FROM linked_account WHERE id = ?",
                        String::class.java,
                        kept.first.value,
                    ),
                ).isEqualTo("connected")
            }
            assertThat(fake.isOpen(kept.second)).isTrue()
            await().atMost(Duration.ofSeconds(15)).until { Delivered.received.isNotEmpty() }
            assertThat(Delivered.received).containsExactly(AccountUnlinked(owner, unlinked))
            await().atMost(Duration.ofSeconds(15)).until { outstanding(jdbc) == 0 }
        } finally {
            second.context.close()
        }
    }

    private fun outstanding(jdbc: JdbcTemplate) =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication " +
                "WHERE event_type LIKE '%AccountUnlinked' AND completion_date IS NULL",
            Int::class.java,
        )!!

    companion object {
        private lateinit var postgres: PostgreSQLContainer
        private lateinit var sessions: Path

        @JvmStatic
        @BeforeAll
        fun database() {
            postgres = PostgreSQLContainer(TestcontainersConfiguration.PGVECTOR_IMAGE).also { it.start() }
            sessions = Files.createTempDirectory("telex-real-restart")
        }

        @JvmStatic
        @AfterAll
        fun stopDatabase() = postgres.stop()
    }
}
