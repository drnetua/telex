package telex.messaging.internal.account

import telex.identity.OwnerId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.MaskedPhone
import telex.telegram.TelegramSessionId
import java.time.Instant

/** The Linked Account aggregate as stored, minus the sealed TDLib key (only written, never read back here). */
data class LinkedAccount(
    val id: LinkedAccountId,
    val ownerId: OwnerId,
    val telegramUserId: Long,
    val telegramSessionId: TelegramSessionId?,
    val displayName: String,
    val phone: MaskedPhone,
    val state: LinkedAccountState,
    val chatsTotal: Int?,
    val chatSyncCompletedAt: Instant?,
    val createdAt: Instant,
)

/** A Linked Account with the number of its synced chats (`COUNT(*)` of its channel rows). */
data class LinkedAccountWithChats(
    val account: LinkedAccount,
    val chatsSynced: Int,
)

/** The values a new Linked Account is inserted with. */
@Suppress("ArrayInDataClass")
data class NewLinkedAccount(
    val id: LinkedAccountId,
    val ownerId: OwnerId,
    val telegramUserId: Long,
    val telegramSessionId: TelegramSessionId,
    val tdlibKeySealed: ByteArray,
    val displayName: String,
    val phone: MaskedPhone,
    val createdAt: Instant,
)
