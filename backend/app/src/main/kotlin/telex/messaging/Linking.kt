package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.attempt.LinkingAttempt
import telex.messaging.internal.attempt.LinkingAttempts
import telex.messaging.internal.config.AccountLimit
import telex.telegram.TelegramSessions
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

enum class LinkingStep { PHONE, CODE, PASSWORD }

/** Where the wizard started: SCR-10 (Inbox) or SCR-60 (Accounts). */
enum class LinkingOrigin { INBOX, ACCOUNTS }

/** What the Owner sees of the open attempt. */
data class LinkingAttemptView(
    val step: LinkingStep,
    val origin: LinkingOrigin,
    val targetLinkedAccountId: LinkedAccountId?,
    val codeLength: Int?,
    val passwordHint: String?,
)

/** [resumed] is true when an attempt was already open (HTTP 200); false for a new one (201). */
data class StartedLinking(
    val attempt: LinkingAttemptView,
    val resumed: Boolean,
)

/**
 * The Owner's one in-memory linking attempt: start or resume, read, cancel and expire (AC-109, AC-110, AC-119).
 * Discarding always closes and destroys the attempt's Telegram session.
 */
@Service
@Suppress("LongParameterList") // collaborators of one service
class Linking(
    private val attempts: LinkingAttempts,
    private val telegram: ObjectProvider<TelegramSessions>,
    private val ownerKeys: OwnerKeys,
    private val signInSessions: SignInSessions,
    private val rows: LinkedAccountRows,
    private val limit: AccountLimit,
    private val clock: Clock,
    private val meters: MeterRegistry,
) {
    private val random = SecureRandom()

    /** Starts the Owner's attempt, or returns the open one at its step (the request body is then ignored). */
    fun start(
        owner: OwnerId,
        signInSession: SignInSessionId,
        origin: LinkingOrigin,
        target: LinkedAccountId?,
    ): StartedLinking =
        attempts.locked(owner) {
            val sessions = telegram.ifAvailable
            if (sessions == null || !sessions.configured() || !ownerKeys.ready()) throw TelegramLinkingNotSetUp()
            val open = liveAttempt(owner)
            if (open != null) {
                StartedLinking(open.view(), resumed = true)
            } else {
                StartedLinking(open(sessions, owner, signInSession, origin, target).view(), resumed = false)
            }
        }

    /** The open attempt; one whose Sign-in Session is gone or that sat idle is discarded and not found. */
    fun get(owner: OwnerId): LinkingAttemptView =
        attempts.locked(owner) { liveAttempt(owner)?.view() ?: throw LinkingAttemptNotFound() }

    /** Discards the attempt; a no-op when none is open. */
    fun cancel(owner: OwnerId) {
        attempts.locked(owner) { discard(owner, "cancelled") }
    }

    /** Discards every attempt idle for 15 minutes or whose Sign-in Session is no longer live. */
    @Scheduled(fixedDelay = SWEEP_MILLIS)
    fun sweep() {
        attempts.owners().forEach { owner -> attempts.locked(owner) { liveAttempt(owner) } }
    }

    private fun open(
        sessions: TelegramSessions,
        owner: OwnerId,
        signInSession: SignInSessionId,
        origin: LinkingOrigin,
        target: LinkedAccountId?,
    ): LinkingAttempt {
        if (target != null) requireSessionLost(owner, target) else requireBelowLimit(owner)
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val attempt = LinkingAttempt(sessions.open(key), key, origin, target, signInSession, clock.instant())
        attempts.put(owner, attempt)
        return attempt
    }

    /** The Owner's attempt if it is still valid; otherwise discards it and returns null. */
    private fun liveAttempt(owner: OwnerId): LinkingAttempt? =
        attempts.find(owner)?.let { attempt ->
            val idle = Duration.between(attempt.lastStepAt, clock.instant()) >= IDLE_LIMIT
            if (idle || !signInSessions.isLive(attempt.signInSession)) {
                discard(owner, "expired")
                null
            } else {
                attempt
            }
        }

    private fun requireSessionLost(
        owner: OwnerId,
        target: LinkedAccountId,
    ) {
        val account = rows.getMine(owner, target) ?: throw LinkedAccountNotFound()
        if (account.state != LinkedAccountState.SESSION_LOST) throw TelegramAccountAlreadyLinked()
    }

    private fun requireBelowLimit(owner: OwnerId) {
        if (rows.countMine(owner) >= limit.maxPerOwner) throw LinkedAccountLimitReached(limit.maxPerOwner)
    }

    private fun discard(
        owner: OwnerId,
        outcome: String,
    ) {
        val attempt = attempts.remove(owner) ?: return
        meters.counter("telex.linking.attempts", "outcome", outcome).increment()
        attempt.wipeKey()
        val sessions = telegram.ifAvailable ?: return
        try {
            sessions.close(attempt.sessionId)
        } finally {
            sessions.destroy(attempt.sessionId)
        }
    }

    private fun LinkingAttempt.view() = LinkingAttemptView(step, origin, target, codeLength, passwordHint)

    private companion object {
        const val KEY_BYTES = 32
        const val SWEEP_MILLIS = 60_000L
        val IDLE_LIMIT: Duration = Duration.ofMinutes(15)
    }
}
