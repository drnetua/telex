package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.shared.Uuid7
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** AC-116 and AC-121: the chat list of a Linked Account is synced into channel rows with throttled progress. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, ChatSyncIT.Probe::class)
class ChatSyncIT {
    class ProgressLog {
        val events = CopyOnWriteArrayList<LinkedAccountSyncProgressed>()

        @EventListener
        fun on(event: LinkedAccountSyncProgressed) {
            events += event
        }
    }

    @TestConfiguration
    class Probe {
        @Bean
        fun progressLog() = ProgressLog()
    }

    @Autowired lateinit var rows: LinkedAccountRows

    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEventPublisher

    @Autowired lateinit var progress: ProgressLog

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var meters: MeterRegistry

    private class Linked(
        val owner: OwnerId,
        val id: LinkedAccountId,
        val session: TelegramSessionId,
    )

    private fun link(): Linked {
        val ownerId = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", ownerId, "$ownerId@mail.com", "$ownerId@mail.com")
        val linked = Linked(OwnerId(ownerId), LinkedAccountId(Uuid7.next()), TelegramSessionId(UUID.randomUUID()))
        rows.insert(
            NewLinkedAccount(
                linked.id,
                linked.owner,
                Uuid7.next().mostSignificantBits,
                linked.session,
                ByteArray(SEALED_KEY_BYTES) { 1 },
                "Anna",
                MaskedPhone("380", "42"),
                Instant.now(),
            ),
        )
        return linked
    }

    private fun chat(
        n: Int,
        archived: Boolean = false,
        title: String = "Secret title $n",
        type: ChatType = ChatType.Supergroup,
    ) = ChatSnapshot(1_000L + n, type, title, listOf(1), archived, n % 3, n.toLong())

    private fun deliver(
        linked: Linked,
        upserted: List<ChatSnapshot> = emptyList(),
        removed: List<Long> = emptyList(),
        total: Int? = null,
        completed: Boolean = false,
        loaded: Set<Long>? = null,
    ) = events.publishEvent(TelegramChatsChanged(linked.session, upserted, removed, total, completed, loaded))

    private fun summary(linked: Linked) = accounts.listMine(linked.owner).single()

    private fun deliverAll(
        linked: Linked,
        chats: List<ChatSnapshot>,
        total: Int,
        batches: Int = chats.size,
    ) {
        val chunks = chats.chunked(BATCH)
        chunks.take(batches).forEachIndexed { i, batch ->
            deliver(linked, batch, total = total, completed = i == chunks.lastIndex)
        }
    }

    @Test
    fun `500 chats with 50 archived sync with the total counting archived ones and the sync finishes`() {
        val linked = link()
        val chats = List(CHATS) { chat(it, archived = it < ARCHIVED) }

        deliver(linked, chats.take(BATCH), total = CHATS)
        assertThat(summary(linked).chatsSynced).isEqualTo(BATCH)
        assertThat(summary(linked).chatsTotal).isEqualTo(CHATS)
        assertThat(summary(linked).chatSyncCompletedAt).isNull()

        chats.drop(BATCH).chunked(BATCH).forEachIndexed { i, batch ->
            deliver(linked, batch, total = CHATS, completed = i == CHATS / BATCH - 2)
        }

        val done = summary(linked)
        assertThat(done.chatsSynced).isEqualTo(CHATS)
        assertThat(done.chatsTotal).isEqualTo(CHATS)
        assertThat(done.chatSyncCompletedAt).isNotNull()
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM channel WHERE archived AND linked_account_id = ?",
                Int::class.java,
                linked.id.value,
            ),
        ).isEqualTo(ARCHIVED)
    }

    @Test
    fun `AC-121 the sync completion time comes from the clock`() {
        val linked = link()

        deliver(linked, listOf(chat(1)), total = 1, completed = true)

        assertThat(summary(linked).chatSyncCompletedAt).isEqualTo(clock.instant())
    }

    @Test
    fun `AC-121 the sync duration is timed once per completed sync`() {
        val linked = link()

        fun samples() = meters.find("telex.chat_sync.duration").timer()?.count() ?: 0L
        val before = samples()

        deliver(linked, listOf(chat(1)), total = 2, completed = true)
        deliver(linked, listOf(chat(2)), total = 2, completed = true)
        deliver(linked, listOf(chat(2, title = "Renamed")), completed = true)

        assertThat(samples() - before).isEqualTo(1L)
    }

    @Test
    fun `AC-116 progress is published in a transaction so no publication stays incomplete`() {
        val linked = link()

        deliver(linked, listOf(chat(1)), total = 1, completed = true)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM event_publication WHERE completion_date IS NULL " +
                        "AND event_type LIKE '%SyncProgressed'",
                    Int::class.java,
                ),
            ).isZero()
        }
    }

    @Test
    fun `progress events are throttled and the last change is never lost`() {
        val linked = link()
        val chats = List(CHATS) { chat(it) }

        deliverAll(linked, chats, CHATS)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(progress.events.count { it.linkedAccountId == linked.id }).isGreaterThanOrEqualTo(2)
        }
        assertThat(progress.events.count { it.linkedAccountId == linked.id }).isLessThan(CHATS / BATCH)
        assertThat(
            progress.events
                .filter { it.linkedAccountId == linked.id }
                .map { it.ownerId }
                .toSet(),
        ).containsExactly(linked.owner)
    }

    @Test
    fun `a chat delivered twice is one row with a stable id`() {
        val linked = link()

        deliver(linked, listOf(chat(1)), total = 2)
        val first =
            jdbc.queryForObject(
                "SELECT id FROM channel WHERE linked_account_id = ?",
                UUID::class.java,
                linked.id.value,
            )
        deliver(linked, listOf(chat(1), chat(2)), total = 2, completed = true)

        assertThat(summary(linked).chatsSynced).isEqualTo(2)
        assertThat(
            jdbc.queryForObject(
                "SELECT id FROM channel WHERE linked_account_id = ? AND telegram_chat_id = ?",
                UUID::class.java,
                linked.id.value,
                chat(1).chatId,
            ),
        ).isEqualTo(first)
    }

    @Test
    fun `join leave and rename update the count and the rows while connected`() {
        val linked = link()
        deliverAll(linked, List(3) { chat(it) }, 3)

        deliver(linked, listOf(chat(10, type = ChatType.Channel)), total = 4)
        assertThat(summary(linked).chatsSynced).isEqualTo(4)
        assertThat(
            jdbc.queryForObject(
                "SELECT type FROM channel WHERE linked_account_id = ? AND telegram_chat_id = ?",
                String::class.java,
                linked.id.value,
                chat(10).chatId,
            ),
        ).isEqualTo("channel")

        deliver(linked, removed = listOf(chat(0).chatId), total = 3)
        assertThat(summary(linked).chatsSynced).isEqualTo(3)
        assertThat(summary(linked).chatsTotal).isEqualTo(3)

        deliver(linked, listOf(chat(1, title = "Renamed")))
        assertThat(
            jdbc.queryForObject(
                "SELECT title FROM channel WHERE linked_account_id = ? AND telegram_chat_id = ?",
                String::class.java,
                linked.id.value,
                chat(1).chatId,
            ),
        ).isEqualTo("Renamed")
        assertThat(summary(linked).chatsSynced).isEqualTo(3)
    }

    @Test
    fun `a restart mid-sync keeps the stored counts and the redelivered list does not double them`() {
        val linked = link()
        val chats = List(CHATS) { chat(it) }

        deliverAll(linked, chats, CHATS, batches = 4)
        assertThat(summary(linked).chatsSynced).isEqualTo(4 * BATCH)
        assertThat(summary(linked).chatSyncCompletedAt).isNull()

        deliverAll(linked, chats, CHATS)

        assertThat(summary(linked).chatsSynced).isEqualTo(CHATS)
        assertThat(summary(linked).chatSyncCompletedAt).isNotNull()
    }

    @Test
    fun `AC-121 a chat absent from a completed load is deleted, so synced never exceeds total`() {
        val linked = link()
        val all = List(3) { chat(it) }
        deliver(linked, all, total = 3, completed = true, loaded = all.map { it.chatId }.toSet())
        assertThat(summary(linked).chatsSynced).isEqualTo(3)

        val kept = listOf(chat(0), chat(2))
        deliver(linked, kept, total = 2, completed = true, loaded = kept.map { it.chatId }.toSet())

        assertThat(summary(linked).chatsSynced).isEqualTo(2)
        assertThat(summary(linked).chatsTotal).isEqualTo(2)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM channel WHERE linked_account_id = ? AND telegram_chat_id = ?",
                Int::class.java,
                linked.id.value,
                chat(1).chatId,
            ),
        ).isZero()
    }

    @Test
    fun `a batch for an unknown session writes nothing`() {
        val ghost =
            Linked(OwnerId(UUID.randomUUID()), LinkedAccountId(Uuid7.next()), TelegramSessionId(UUID.randomUUID()))
        val before = jdbc.queryForObject("SELECT count(*) FROM channel", Int::class.java)

        deliver(ghost, listOf(chat(1)), total = 1, completed = true)

        assertThat(jdbc.queryForObject("SELECT count(*) FROM channel", Int::class.java)).isEqualTo(before)
    }

    @Test
    fun `no chat title or telegram id enters the event publication registry`() {
        val linked = link()
        deliverAll(linked, listOf(chat(7, title = "Very private title")), 1)

        await().atMost(Duration.ofSeconds(3)).until { progress.events.any { it.linkedAccountId == linked.id } }

        val serialized = jdbc.queryForList("SELECT serialized_event FROM event_publication", String::class.java)
        assertThat(serialized).noneMatch {
            it.orEmpty().contains("Very private title") ||
                it.orEmpty().contains(chat(7).chatId.toString())
        }
    }

    companion object {
        private const val SEALED_KEY_BYTES = 60
        private const val CHATS = 500
        private const val ARCHIVED = 50
        private const val BATCH = 50
    }
}
