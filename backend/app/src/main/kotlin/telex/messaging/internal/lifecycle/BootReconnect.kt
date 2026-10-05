package telex.messaging.internal.lifecycle

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import telex.identity.OwnerKeys
import telex.messaging.LinkedAccountState
import telex.messaging.internal.account.LinkedAccount
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.keyAad
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUnavailable
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Brings the Linked Accounts back after a start (US-52): sweeps the session directories nobody references, then
 * reopens every account that is not Session lost, each on its own virtual thread, so readiness never waits for
 * Telegram. The accounts' states then follow the session events ([SessionStateListener]).
 * After a master-key reset every account is Session lost instead and every directory goes.
 */
@Component
class BootReconnect(
    private val rows: LinkedAccountRows,
    private val ownerKeys: OwnerKeys,
    private val telegram: TelegramSessions,
    private val states: LinkedAccountStates,
    private val listener: SessionStateListener,
    private val metrics: LifecycleMetrics,
) {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        run()
    }

    /** Starts the boot work and returns once every reopen finished; callers that must not wait ignore the result. */
    fun run(): CompletableFuture<Void> {
        if (ownerKeys.resetPerformedAtStartup) {
            states.resetAll()
            telegram.sweepOrphans(emptySet())
            return CompletableFuture.completedFuture(null)
        }
        telegram.sweepOrphans(rows.allSessionIds())
        val reopens = rows.allNotSessionLost().map { CompletableFuture.runAsync({ reopen(it) }, executor) }
        return CompletableFuture.allOf(*reopens.toTypedArray())
    }

    @Suppress("TooGenericExceptionCaught") // one account failing to reopen must not stop the others
    private fun reopen(account: LinkedAccount) {
        val session = account.telegramSessionId ?: return
        try {
            val sealed = checkNotNull(rows.sealedKey(account.id)) { "Account has no sealed key" }
            val key = ownerKeys.open(account.ownerId, sealed, account.id.keyAad())
            try {
                listener.forget(session)
                metrics.reconnectStarted(session)
                telegram.reopen(session, key)
            } finally {
                key.fill(0)
            }
            dropIfUnlinked(account, session)
        } catch (e: Exception) {
            // Not a Telegram confirmation, so never Session lost (AC-122); only a failure to reach Telegram is shown.
            log.warn("Could not reopen Linked Account {}", account.id.value, e)
            if (e is TelegramUnavailable) states.transition(session, LinkedAccountState.RECONNECTING)
            removeIfUnlinked(session)
        }
    }

    /** A reopen that failed after an unlink recreated the directory of a session no account holds any more. */
    @Suppress("TooGenericExceptionCaught") // cleanup after a failure must not throw
    private fun removeIfUnlinked(session: TelegramSessionId) {
        try {
            if (rows.findBySession(session) == null) telegram.destroy(session)
        } catch (e: Exception) {
            log.warn("Could not remove the directory of an unlinked session {}", session.value, e)
        }
    }

    /**
     * An unlink that ran while the session was still being reopened found no open session, so it could neither sign
     * it out nor close it, and its directory deletion raced the reopen (AC-111, AC-113). The session is registered
     * now: if no account holds it any more, it is closed and destroyed here, so no client runs for a deleted account.
     */
    private fun dropIfUnlinked(
        account: LinkedAccount,
        session: TelegramSessionId,
    ) {
        if (rows.findBySession(session) != null) return
        log.info("Linked Account {} was unlinked while it reopened; closing its session", account.id.value)
        try {
            // the unlink found no session to sign out; the teleX device must not stay among Telegram's sessions
            telegram.logOut(session, SIGN_OUT_TIMEOUT)
        } finally {
            try {
                telegram.close(session)
            } finally {
                telegram.destroy(session)
            }
        }
    }

    @PreDestroy
    fun shutdown() = executor.shutdown()

    private companion object {
        val log = LoggerFactory.getLogger(BootReconnect::class.java)
        val SIGN_OUT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
