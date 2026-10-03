package telex.messaging.internal.account

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import telex.identity.OwnerId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.MaskedPhone
import telex.telegram.TelegramSessionId
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Linked Account persistence. Every query that knows the Owner filters on `owner_id` (AC-03). */
@Repository
class LinkedAccountRows(
    private val jdbc: JdbcClient,
) {
    fun insert(account: NewLinkedAccount) {
        jdbc
            .sql(
                "INSERT INTO linked_account (id, owner_id, telegram_user_id, telegram_session_id, " +
                    "tdlib_key_sealed, display_name, phone_country_code, phone_last_digits, state, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).params(
                account.id.value,
                account.ownerId.value,
                account.telegramUserId,
                account.telegramSessionId.value,
                account.tdlibKeySealed,
                account.displayName,
                account.phone.countryCode,
                account.phone.lastDigits,
                LinkedAccountState.CONNECTED.wire,
                OffsetDateTime.ofInstant(account.createdAt, ZoneOffset.UTC),
            ).update()
    }

    fun listMine(owner: OwnerId): List<LinkedAccountWithChats> =
        jdbc
            .sql(
                "SELECT a.*, (SELECT count(*) FROM channel c WHERE c.linked_account_id = a.id " +
                    "AND c.owner_id = a.owner_id) AS chats_synced " +
                    "FROM linked_account a WHERE a.owner_id = ? ORDER BY a.created_at, a.id",
            ).param(owner.value)
            .query { rs, _ -> LinkedAccountWithChats(account(rs), rs.getInt("chats_synced")) }
            .list()

    fun countMine(owner: OwnerId): Int =
        jdbc
            .sql("SELECT count(*) FROM linked_account WHERE owner_id = ?")
            .param(owner.value)
            .query(Int::class.java)
            .single()

    /**
     * The count for the limit check inside the insert transaction. Takes a transaction-scoped advisory lock keyed
     * by the Owner, so two wizards finishing together cannot both pass (AC-115). Needs an open transaction.
     */
    fun countMineLocked(owner: OwnerId): Int {
        jdbc
            .sql("SELECT pg_advisory_xact_lock(hashtext(?))")
            .param("linked_account:" + owner.value)
            .query()
            .singleRow()
        return countMine(owner)
    }

    fun getMine(
        owner: OwnerId,
        id: LinkedAccountId,
    ): LinkedAccount? = find("id = ? AND owner_id = ?", id.value, owner.value)

    /** Who holds this Telegram user, any Owner: the decision needs it to tell AC-04 from AC-108. */
    fun findByTelegramUser(telegramUserId: Long): LinkedAccount? = find("telegram_user_id = ?", telegramUserId)

    fun findBySession(sessionId: TelegramSessionId): LinkedAccount? = find("telegram_session_id = ?", sessionId.value)

    fun allNotSessionLost(): List<LinkedAccount> =
        jdbc
            .sql("SELECT * FROM linked_account WHERE state <> 'session_lost' ORDER BY created_at, id")
            .query { rs, _ -> account(rs) }
            .list()

    fun allSessionIds(): Set<TelegramSessionId> =
        jdbc
            .sql("SELECT telegram_session_id FROM linked_account WHERE telegram_session_id IS NOT NULL")
            .query { rs, _ -> TelegramSessionId(rs.getObject(1, UUID::class.java)) }
            .set()

    private fun find(
        where: String,
        vararg args: Any,
    ): LinkedAccount? =
        jdbc
            .sql("SELECT * FROM linked_account WHERE $where")
            .params(args.toList())
            .query { rs, _ -> account(rs) }
            .optional()
            .orElse(null)

    private fun account(rs: ResultSet) =
        LinkedAccount(
            id = LinkedAccountId(rs.getObject("id", UUID::class.java)),
            ownerId = OwnerId(rs.getObject("owner_id", UUID::class.java)),
            telegramUserId = rs.getLong("telegram_user_id"),
            telegramSessionId = rs.getObject("telegram_session_id", UUID::class.java)?.let { TelegramSessionId(it) },
            displayName = rs.getString("display_name"),
            phone = MaskedPhone(rs.getString("phone_country_code"), rs.getString("phone_last_digits")),
            state = LinkedAccountState.fromWire(rs.getString("state")),
            chatsTotal = rs.getInt("chats_total").takeUnless { rs.wasNull() },
            chatSyncCompletedAt = rs.getObject("chat_sync_completed_at", OffsetDateTime::class.java)?.toInstant(),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        )
}
