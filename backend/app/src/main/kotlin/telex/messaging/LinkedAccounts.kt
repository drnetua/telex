package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.messaging.internal.account.AccountDeletion
import telex.messaging.internal.account.DeletedRow
import telex.messaging.internal.account.LinkedAccount
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.lifecycle.LinkedAccountStates
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
    private val ownerKeys: OwnerKeys,
    private val states: LinkedAccountStates,
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
     * confirm never stops the delete. If the delete fails, the account is Session lost after a confirmed sign-out and
     * its session is left open after an unconfirmed one. An interrupt during the sign-out is handed back on return.
     */
    fun unlink(
        owner: OwnerId,
        id: LinkedAccountId,
    ): UnlinkResult {
        // The attempt goes first, so a targeted Sign in again completing later fails. An untargeted one can still swap
        // the session after the read below: the delete reports the session it removed and that one is destroyed too.
        linking.discardTargeting(owner, id)
        val account = rows.getMine(owner, id) ?: throw LinkedAccountNotFound()
        val sessions = telegram.ifAvailable
        val signedOut = account.telegramSessionId
        var interrupted = false
        try {
            // A Session lost account has no open session to sign out; Telegram already ended it.
            var confirmed =
                account.state != LinkedAccountState.SESSION_LOST && signedOut != null && sessions != null &&
                    signOut(sessions, signedOut, account) { interrupted = true }
            // an interrupt the sign-out didn't take (it was skipped, or the interrupt came after it) fails the delete
            if (Thread.interrupted()) interrupted = true
            val removed = deleteOrClose(owner, id, sessions, signedOut, confirmed) ?: throw LinkedAccountNotFound()
            val session = removed.session
            if (session != null && sessions != null) {
                // The row held another session than the one read: it is signed out and destroyed too.
                if (session != signedOut) confirmed = signOut(sessions, session) { interrupted = true }
                try {
                    sessions.close(session)
                } finally {
                    sessions.destroy(session)
                }
            }
            meters.counter("telex.unlink", "signout", if (confirmed) "confirmed" else "unconfirmed").increment()
            return UnlinkResult(confirmed)
        } finally {
            // only now: on a virtual request thread marked interrupted, the delete's JDBC calls would fail
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    /**
     * A failed delete leaves the row. After a confirmed sign-out Telegram really ended the session, so it is closed
     * and the account becomes Session lost (the listener's transition: same event and banner), not a connected row with
     * no client. After an unconfirmed one the session stays open: it reports its state again and may reconnect.
     */
    @Suppress("TooGenericExceptionCaught") // whatever the delete throws, a confirmed sign-out must not stay connected
    private fun deleteOrClose(
        owner: OwnerId,
        id: LinkedAccountId,
        sessions: TelegramSessions?,
        signedOut: TelegramSessionId?,
        confirmed: Boolean,
    ): DeletedRow? =
        try {
            deletion.delete(owner, id)
        } catch (e: RuntimeException) {
            if (confirmed && signedOut != null && sessions != null) markLost(sessions, signedOut, e)
            throw e
        }

    @Suppress("TooGenericExceptionCaught") // a failed close or transition must not hide the delete failure
    private fun markLost(
        sessions: TelegramSessions,
        session: TelegramSessionId,
        failure: RuntimeException,
    ) {
        // separate steps: a failed close must not leave the row connected
        try {
            sessions.close(session)
        } catch (secondary: Exception) {
            failure.addSuppressed(secondary)
        }
        try {
            states.transition(session, LinkedAccountState.SESSION_LOST)
        } catch (secondary: Exception) {
            failure.addSuppressed(secondary)
        }
    }

    /**
     * A session the port does not know (boot has not reached it yet) is reopened with the account's key, so the
     * sign-out can reach Telegram and no teleX device stays among its active sessions (AC-111).
     */
    private fun reopenIfClosed(
        sessions: TelegramSessions,
        account: LinkedAccount,
        session: TelegramSessionId,
    ) {
        if (sessions.isOpen(session)) return
        val sealed = rows.sealedKey(account.id) ?: return
        val key = ownerKeys.open(account.ownerId, sealed, account.id.keyAad())
        try {
            sessions.reopen(session, key)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Returns with the thread's interrupt cleared and reported to [onInterrupt], however it arrived: as an
     * `InterruptedException`, or as another failure that left it set (a JDBC read on an interrupted virtual thread).
     */
    @Suppress("TooGenericExceptionCaught") // any failure to open the key or reach Telegram means "not confirmed"
    private fun signOut(
        sessions: TelegramSessions,
        session: TelegramSessionId,
        reopenFor: LinkedAccount? = null,
        onInterrupt: () -> Unit,
    ): Boolean {
        val confirmed =
            try {
                if (reopenFor != null) reopenIfClosed(sessions, reopenFor, session)
                sessions.logOut(session, SIGN_OUT_TIMEOUT)
            } catch (_: InterruptedException) {
                onInterrupt()
                false
            } catch (_: Exception) {
                false
            }
        if (Thread.interrupted()) onInterrupt()
        return confirmed
    }

    private companion object {
        val SIGN_OUT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
