package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.OwnerId
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.account.LinkedAccountRows
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import java.time.Duration
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

/** The result of an unlink: whether Telegram confirmed the sign-out (otherwise the Owner checks active sessions). */
data class UnlinkResult(
    val signOutConfirmed: Boolean,
)

/** Public API of Linked Accounts. Owner-scoped: another Owner's account behaves as missing (AC-03). */
@Service
class LinkedAccounts(
    private val rows: LinkedAccountRows,
    private val deletion: AccountDeletion,
    private val linking: Linking,
    private val telegram: ObjectProvider<TelegramSessions>,
    private val meters: MeterRegistry,
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

    /**
     * Unlinks the Owner's account (AC-111, AC-113): a bounded sign-out, then one transaction that deletes the
     * account and records `AccountUnlinked`, then the session is closed and destroyed. A sign-out Telegram does not
     * confirm never stops the delete.
     */
    fun unlink(
        owner: OwnerId,
        id: LinkedAccountId,
    ): UnlinkResult {
        // The attempt goes first: a Sign in again completing later fails, so the session read below is final.
        linking.discardTargeting(owner, id)
        val account = rows.getMine(owner, id) ?: throw LinkedAccountNotFound()
        val sessions = telegram.ifAvailable
        val signedOut = account.telegramSessionId
        // A Session lost account has no open session to sign out; Telegram already ended it.
        var confirmed =
            account.state != LinkedAccountState.SESSION_LOST && signedOut != null && sessions != null &&
                signOut(sessions, signedOut)
        val removed = deletion.delete(owner, id) ?: throw LinkedAccountNotFound()
        val session = removed.session
        if (session != null && sessions != null) {
            // The row held another session than the one read: it is signed out and destroyed too.
            if (session != signedOut) confirmed = signOut(sessions, session)
            try {
                sessions.close(session)
            } finally {
                sessions.destroy(session)
            }
        }
        meters.counter("telex.unlink", "signout", if (confirmed) "confirmed" else "unconfirmed").increment()
        return UnlinkResult(confirmed)
    }

    @Suppress("TooGenericExceptionCaught") // any failure to reach Telegram means "not confirmed"
    private fun signOut(
        sessions: TelegramSessions,
        session: TelegramSessionId,
    ): Boolean =
        try {
            sessions.logOut(session, SIGN_OUT_TIMEOUT)
        } catch (_: RuntimeException) {
            false
        }

    private companion object {
        val SIGN_OUT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
