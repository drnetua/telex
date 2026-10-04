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
        // The attempt goes first, so a targeted Sign in again completing later fails. An untargeted one can still swap
        // the session after the read below: the delete reports the session it removed and that one is destroyed too.
        linking.discardTargeting(owner, id)
        val account = rows.getMine(owner, id) ?: throw LinkedAccountNotFound()
        val sessions = telegram.ifAvailable
        val signedOut = account.telegramSessionId
        // A Session lost account has no open session to sign out; Telegram already ended it.
        var confirmed =
            account.state != LinkedAccountState.SESSION_LOST && signedOut != null && sessions != null &&
                signOut(sessions, signedOut, account)
        val removed = deleteOrClose(owner, id, sessions, signedOut) ?: throw LinkedAccountNotFound()
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

    @Suppress("TooGenericExceptionCaught") // whatever the delete throws, the signed-out session must not stay open
    private fun deleteOrClose(
        owner: OwnerId,
        id: LinkedAccountId,
        sessions: TelegramSessions?,
        signedOut: TelegramSessionId?,
    ): DeletedRow? =
        try {
            deletion.delete(owner, id)
        } catch (e: RuntimeException) {
            // the row stays: a signed-out session left open would be muted and never report again
            if (signedOut != null && sessions != null) closeQuietly(sessions, signedOut, e)
            throw e
        }

    @Suppress("TooGenericExceptionCaught") // a failed close must not hide the delete failure
    private fun closeQuietly(
        sessions: TelegramSessions,
        session: TelegramSessionId,
        failure: RuntimeException,
    ) {
        try {
            sessions.close(session)
        } catch (closeFailure: RuntimeException) {
            failure.addSuppressed(closeFailure)
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

    @Suppress("TooGenericExceptionCaught") // any failure to open the key or reach Telegram means "not confirmed"
    private fun signOut(
        sessions: TelegramSessions,
        session: TelegramSessionId,
        reopenFor: LinkedAccount? = null,
    ): Boolean =
        try {
            if (reopenFor != null) reopenIfClosed(sessions, reopenFor, session)
            sessions.logOut(session, SIGN_OUT_TIMEOUT)
        } catch (_: Exception) {
            false
        }

    private companion object {
        val SIGN_OUT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
