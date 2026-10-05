package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import telex.TestcontainersConfiguration
import telex.identity.OwnerId
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.account.NewLinkedAccount
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Owner scoping (AC-03), chat count, the advisory-locked count (AC-115) and the lookups behind AC-04 / AC-108. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class LinkedAccountRepositoryIT {
    @Autowired lateinit var rows: LinkedAccountRows

    @Autowired lateinit var accounts: LinkedAccounts

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var tx: TransactionTemplate

    private fun owner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun link(
        owner: OwnerId,
        telegramUserId: Long = nextTelegramUserId(),
        session: TelegramSessionId = TelegramSessionId(UUID.randomUUID()),
    ): NewLinkedAccount {
        val account =
            NewLinkedAccount(
                LinkedAccountId(Uuid7.next()),
                owner,
                telegramUserId,
                session,
                ByteArray(SEALED_KEY_BYTES) { 1 },
                "Anna",
                MaskedPhone("380", "42"),
                Instant.now(),
            )
        rows.insert(account)
        return account
    }

    private fun chat(
        owner: OwnerId,
        account: LinkedAccountId,
        chatId: Long,
    ) = jdbc.update(
        "INSERT INTO channel VALUES (gen_random_uuid(), ?, ?, ?, 'private', 'Chat', '{}', false, 0, 0)",
        owner.value,
        account.value,
        chatId,
    )

    @Test
    fun `listMine returns only my accounts with their synced chat count and masked phone`() {
        val me = owner()
        val other = owner()
        val mine = link(me)
        link(other)
        chat(me, mine.id, 1)
        chat(me, mine.id, 2)

        val listed = accounts.listMine(me)

        assertThat(listed).hasSize(1)
        assertThat(listed.single().id).isEqualTo(mine.id)
        assertThat(listed.single().chatsSynced).isEqualTo(2)
        assertThat(listed.single().phone).isEqualTo(MaskedPhone("380", "42"))
        assertThat(listed.single().state).isEqualTo(LinkedAccountState.CONNECTED)
        assertThat(accounts.countMine(me)).isEqualTo(1)
    }

    @Test
    fun `another Owner's account behaves as missing`() {
        val me = owner()
        val other = owner()
        val theirs = link(other)

        assertThat(rows.getMine(me, theirs.id)).isNull()
        assertThat(rows.getMine(other, theirs.id)).isNotNull()
        assertThat(accounts.listMine(me)).isEmpty()
    }

    @Test
    fun `lookups find who owns a Telegram user and the account of a session`() {
        val me = owner()
        val session = TelegramSessionId(UUID.randomUUID())
        val telegramUserId = nextTelegramUserId()
        val account = link(me, telegramUserId, session)

        assertThat(rows.findByTelegramUser(telegramUserId)?.ownerId).isEqualTo(me)
        assertThat(rows.findByTelegramUser(telegramUserId)?.id).isEqualTo(account.id)
        assertThat(rows.findByTelegramUser(telegramUserId + 1_000_000)).isNull()
        assertThat(rows.findBySession(session)?.id).isEqualTo(account.id)
        assertThat(rows.allSessionIds()).contains(session)
        assertThat(rows.allNotSessionLost().map { it.id }).contains(account.id)
    }

    @Test
    fun `session lost accounts are left out of the boot list but still counted`() {
        val me = owner()
        val account = link(me)
        jdbc.update(
            "UPDATE linked_account SET state = 'session_lost', telegram_session_id = NULL, tdlib_key_sealed = NULL " +
                "WHERE id = ?",
            account.id.value,
        )

        assertThat(rows.allNotSessionLost().map { it.id }).doesNotContain(account.id)
        assertThat(rows.countMine(me)).isEqualTo(1)
        assertThat(rows.getMine(me, account.id)?.state).isEqualTo(LinkedAccountState.SESSION_LOST)
        assertThat(rows.getMine(me, account.id)?.telegramSessionId).isNull()
    }

    @Test
    fun `the locked count waits for a concurrent transaction of the same Owner`() {
        val me = owner()
        link(me)
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<Int> {
                    tx.execute {
                        rows.countMineLocked(me)
                        holding.countDown()
                        release.await(10, TimeUnit.SECONDS)
                        link(me)
                        rows.countMine(me)
                    }
                }
            holding.await(10, TimeUnit.SECONDS)
            val second = pool.submit<Int> { tx.execute { rows.countMineLocked(me) } }
            Thread.sleep(SETTLE_MILLIS)
            assertThat(second.isDone).describedAs("second count must wait for the lock").isFalse()
            release.countDown()
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(2)
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(2)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `the lock of one Owner does not block another Owner`() {
        val a = owner()
        val b = owner()
        tx.execute {
            rows.countMineLocked(a)
            val pool = Executors.newSingleThreadExecutor()
            try {
                assertThat(pool.submit<Int> { tx.execute { rows.countMineLocked(b) } }.get(10, TimeUnit.SECONDS))
                    .isZero()
            } finally {
                pool.shutdownNow()
            }
        }
    }

    companion object {
        private const val SEALED_KEY_BYTES = 60
        private const val SETTLE_MILLIS = 500L
        private var telegramUserSeq = 5_000_000L

        @Synchronized
        private fun nextTelegramUserId() = telegramUserSeq++
    }
}
