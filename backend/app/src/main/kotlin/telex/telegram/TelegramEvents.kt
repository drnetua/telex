package telex.telegram

enum class SessionState { Ready, Connecting, Closed }

/**
 * Plain in-process Spring event (not durable, not externalized). Only [SessionState.Closed] means the session is lost;
 * an outage is [SessionState.Connecting] (AC-122). [sequence] strictly increases per session.
 */
data class TelegramSessionStateChanged(
    val sessionId: TelegramSessionId,
    val state: SessionState,
    val sequence: Long,
)

enum class ChatType { Private, Secret, BasicGroup, Supergroup, Channel }

data class ChatSnapshot(
    val chatId: Long,
    val type: ChatType,
    val title: String,
    val folderIds: List<Int>,
    val archived: Boolean,
    val unreadCount: Int,
    val order: Long,
)

/**
 * Plain in-process Spring event carrying one batch of the chat list sync. [total] is Telegram's total for the account
 * (archived included), or null until known. [loadedChatIds] is set only on the event that completes a load: every
 * chat the load found, so chats not in it are gone.
 */
data class TelegramChatsChanged(
    val sessionId: TelegramSessionId,
    val upserted: List<ChatSnapshot>,
    val removedChatIds: List<Long>,
    val total: Int?,
    val loadCompleted: Boolean,
    val loadedChatIds: Set<Long>? = null,
)
