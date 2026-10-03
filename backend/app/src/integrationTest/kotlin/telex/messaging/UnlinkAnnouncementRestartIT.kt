package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.OwnerId
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private const val SEALED_BYTES = 60

/** AC-112 (QG-1c): an `AccountUnlinked` the process never delivered is republished on restart and delivered. */
@SpringBootTest(properties = ["telex.telegram.adapter=fake"])
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, UnlinkAnnouncementRestartIT.Probe::class)
class UnlinkAnnouncementRestartIT {
    /** State lives outside the listener bean, which Spring proxies for its transaction. */
    object Delivered {
        val received = CopyOnWriteArrayList<AccountUnlinked>()
        val down = AtomicBoolean(true)
    }

    open class Listener {
        @ApplicationModuleListener
        open fun on(event: AccountUnlinked) {
            // while "the process is down" the delivery fails and its registry row stays incomplete
            check(!Delivered.down.get()) { "stopped before delivery" }
            Delivered.received += event
        }
    }

    @TestConfiguration
    class Probe {
        @Bean
        fun listener() = Listener()
    }

    @Autowired lateinit var deletion: AccountDeletion

    @Autowired lateinit var rows: LinkedAccountRows

    @Autowired lateinit var incomplete: IncompleteEventPublications

    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clean() {
        jdbc.execute("DELETE FROM event_publication")
        jdbc.execute("DELETE FROM channel")
        jdbc.execute("DELETE FROM linked_account")
        Delivered.received.clear()
        Delivered.down.set(true)
    }

    @Test
    fun `AC-112 an unlink committed before delivery is republished after the restart and delivered`() {
        val uuid = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", uuid, "$uuid@mail.com", "$uuid@mail.com")
        val owner = OwnerId(uuid)
        val id = LinkedAccountId(Uuid7.next())
        rows.insert(
            NewLinkedAccount(
                id,
                owner,
                Uuid7.next().mostSignificantBits,
                TelegramSessionId(UUID.randomUUID()),
                ByteArray(SEALED_BYTES) { 1 },
                "Anna",
                MaskedPhone("99", "00"),
                Instant.now(),
            ),
        )

        assertThat(deletion.delete(owner, id)).isTrue()

        // committed, but the listener never completed: the registry holds the event as outstanding
        await().atMost(Duration.ofSeconds(5)).until { outstanding() >= 1 }
        assertThat(Delivered.received).isEmpty()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM linked_account WHERE id = ?", Int::class.java, id.value))
            .isZero()

        // the restart: whatever is outstanding is republished (republish-outstanding-events-on-restart)
        Delivered.down.set(false)
        incomplete.resubmitIncompletePublications { it.event is AccountUnlinked }

        await().atMost(Duration.ofSeconds(10)).until { Delivered.received.isNotEmpty() }
        assertThat(Delivered.received).contains(AccountUnlinked(owner, id))
        await().atMost(Duration.ofSeconds(10)).until { outstanding() == 0 }
    }

    private fun outstanding() =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication " +
                "WHERE event_type LIKE '%AccountUnlinked' AND completion_date IS NULL",
            Int::class.java,
        )!!

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { Files.createTempDirectory("telex-restart").toString() }
            registry.add("telex.telegram.api-id") { "54321" }
            registry.add("telex.telegram.api-hash") { "abcdef" }
            registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 9 }) }
        }
    }
}
