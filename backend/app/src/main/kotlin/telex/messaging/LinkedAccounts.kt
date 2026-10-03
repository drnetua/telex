package telex.messaging

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.OwnerId
import telex.messaging.internal.account.LinkedAccountRows
import java.time.Instant

/** What the Owner sees of one Linked Account. No Telegram user id, no full phone number. */
data class LinkedAccountSummary(
    val id: LinkedAccountId,
    val displayName: String,
    val phone: MaskedPhone,
    val state: LinkedAccountState,
    val chatsSynced: Int,
    val chatsTotal: Int?,
    val chatSyncCompletedAt: Instant?,
    val createdAt: Instant,
)

/** Public read API of Linked Accounts. Owner-scoped: another Owner's account behaves as missing (AC-03). */
@Service
class LinkedAccounts(
    private val rows: LinkedAccountRows,
) {
    @Transactional(readOnly = true)
    fun listMine(ownerId: OwnerId): List<LinkedAccountSummary> =
        rows.listMine(ownerId).map {
            LinkedAccountSummary(
                it.account.id,
                it.account.displayName,
                it.account.phone,
                it.account.state,
                it.chatsSynced,
                it.account.chatsTotal,
                it.account.chatSyncCompletedAt,
                it.account.createdAt,
            )
        }

    @Transactional(readOnly = true)
    fun countMine(ownerId: OwnerId): Int = rows.countMine(ownerId)
}
