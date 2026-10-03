package telex.messaging.internal.channel

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import telex.messaging.LinkedAccountId
import telex.shared.Uuid7
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import java.sql.Types
import java.util.UUID

/** Chat-list persistence: `channel` rows of one Linked Account, keyed by (account, Telegram chat id). */
@Repository
class ChannelRows(
    private val jdbc: JdbcTemplate,
) {
    fun upsert(
        ownerId: UUID,
        account: LinkedAccountId,
        chats: List<ChatSnapshot>,
    ) {
        if (chats.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO channel (id, owner_id, linked_account_id, telegram_chat_id, type, title, folder_ids, " +
                "archived, unread_count, chat_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (linked_account_id, telegram_chat_id) DO UPDATE SET type = EXCLUDED.type, " +
                "title = EXCLUDED.title, folder_ids = EXCLUDED.folder_ids, archived = EXCLUDED.archived, " +
                "unread_count = EXCLUDED.unread_count, chat_order = EXCLUDED.chat_order",
            chats,
            BATCH,
        ) { ps, chat ->
            val values =
                listOf(
                    Uuid7.next(),
                    ownerId,
                    account.value,
                    chat.chatId,
                    wire(chat.type),
                    chat.title.take(TITLE_MAX),
                    ps.connection.createArrayOf("integer", chat.folderIds.toTypedArray()),
                    chat.archived,
                    chat.unreadCount,
                    chat.order,
                )
            values.forEachIndexed { index, value -> ps.setObject(index + 1, value) }
        }
    }

    fun delete(
        account: LinkedAccountId,
        chatIds: List<Long>,
    ) {
        if (chatIds.isEmpty()) return
        jdbc.update("DELETE FROM channel WHERE linked_account_id = ? AND telegram_chat_id = ANY(?)") { ps ->
            ps.setObject(1, account.value)
            ps.setArray(2, ps.connection.createArrayOf("bigint", chatIds.toTypedArray()))
        }
    }

    /** Stores Telegram's reported total (archived included); [completed] also stamps the finish time. */
    fun recordProgress(
        account: LinkedAccountId,
        total: Int?,
        completed: Boolean,
    ) {
        if (total == null && !completed) return
        jdbc.update(
            "UPDATE linked_account SET chats_total = COALESCE(?, chats_total, " +
                "(SELECT count(*) FROM channel WHERE linked_account_id = ?)), " +
                "chat_sync_completed_at = CASE WHEN ? THEN COALESCE(chat_sync_completed_at, now()) " +
                "ELSE chat_sync_completed_at END WHERE id = ?",
        ) { ps ->
            listOf(total, account.value, completed, account.value).forEachIndexed { index, value ->
                if (value == null) ps.setNull(index + 1, Types.INTEGER) else ps.setObject(index + 1, value)
            }
        }
    }

    private fun wire(type: ChatType) =
        when (type) {
            ChatType.Private -> "private"
            ChatType.Secret -> "secret"
            ChatType.BasicGroup -> "basic_group"
            ChatType.Supergroup -> "supergroup"
            ChatType.Channel -> "channel"
        }

    private companion object {
        const val BATCH = 500
        const val TITLE_MAX = 255
    }
}
