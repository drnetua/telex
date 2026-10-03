package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUnavailable
import telex.telegram.TelegramUser
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

/** The answer to a wizard step: the attempt moved (or stayed) at a step, or Telegram authorized the account. */
sealed interface LinkingProgress {
    data class Step(
        val attempt: LinkingAttemptView,
    ) : LinkingProgress

    data class Authorized(
        val user: TelegramUser,
    ) : LinkingProgress
}

/**
 * The Owner's one in-memory linking attempt: start or resume, read, cancel and expire (AC-109, AC-110, AC-119).
 * Discarding always closes and destroys the attempt's Telegram session.
 */
@Service
@Suppress("LongParameterList", "TooManyFunctions") // collaborators and the wizard steps of one service
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

    /** Sends the phone number (digits only) to Telegram: code step, or Telegram's refusal for the number. */
    fun submitPhone(
        owner: OwnerId,
        by: SignInSessionId,
        phone: String,
    ): LinkingProgress =
        step(owner, by, LinkingStep.PHONE) { attempt, sessions ->
            val digits = phone.filter(Char::isDigit)
            if (digits.isEmpty()) throw TelegramPhoneInvalid()
            codeSent(owner, attempt, sessions.sendPhone(attempt.sessionId, digits))
        }

    /** Asks Telegram for a new code; the attempt stays at the code step. */
    fun resendCode(
        owner: OwnerId,
        by: SignInSessionId,
    ): LinkingProgress =
        step(owner, by, LinkingStep.CODE) { attempt, sessions ->
            codeSent(owner, attempt, sessions.resendCode(attempt.sessionId))
        }

    /** The code goes straight to Telegram; it is never stored or echoed. */
    fun submitCode(
        owner: OwnerId,
        by: SignInSessionId,
        code: String,
    ): LinkingProgress =
        step(owner, by, LinkingStep.CODE) { attempt, sessions ->
            when (val outcome = sessions.checkCode(attempt.sessionId, code)) {
                is SignInOutcome.PasswordNeeded -> {
                    attempt.step = LinkingStep.PASSWORD
                    attempt.passwordHint = outcome.hint
                    LinkingProgress.Step(attempt.view())
                }

                is SignInOutcome.Authorized -> {
                    authorized(outcome)
                }

                SignInOutcome.CodeWrong -> {
                    throw TelegramCodeWrong()
                }

                SignInOutcome.CodeExpired -> {
                    throw TelegramCodeExpired()
                }

                else -> {
                    refuseOrFail(owner, outcome)
                }
            }
        }

    /** The password goes straight to Telegram; it is never stored or echoed. */
    fun submitPassword(
        owner: OwnerId,
        by: SignInSessionId,
        password: String,
    ): LinkingProgress =
        step(owner, by, LinkingStep.PASSWORD) { attempt, sessions ->
            when (val outcome = sessions.checkPassword(attempt.sessionId, password)) {
                is SignInOutcome.Authorized -> authorized(outcome)
                is SignInOutcome.PasswordWrong -> throw TelegramPasswordWrong(outcome.hint)
                else -> refuseOrFail(owner, outcome)
            }
        }

    /**
     * The guard shared by the four steps: the Owner's live attempt, at the expected step. Records the step time
     * and times the step; a step Telegram does not answer leaves the attempt where it was.
     */
    private fun step(
        owner: OwnerId,
        by: SignInSessionId,
        expected: LinkingStep,
        block: (LinkingAttempt, TelegramSessions) -> LinkingProgress,
    ): LinkingProgress =
        attempts.locked(owner) {
            val attempt = liveAttempt(owner) ?: throw LinkingAttemptNotFound()
            if (attempt.step != expected) throw LinkingStepMismatch(attempt.step)
            val sessions = telegram.ifAvailable ?: throw TelegramLinkingNotSetUp()
            attempt.stepped(by, clock.instant())
            val timer = Timer.start(meters)
            try {
                block(attempt, sessions)
            } catch (_: TelegramUnavailable) {
                throw TelegramUnavailableProblem()
            } finally {
                timer.stop(meters.timer("telex.linking.step.duration", "step", expected.name.lowercase()))
            }
        }

    private fun codeSent(
        owner: OwnerId,
        attempt: LinkingAttempt,
        outcome: SignInOutcome,
    ): LinkingProgress =
        when (outcome) {
            is SignInOutcome.CodeSent -> {
                attempt.step = LinkingStep.CODE
                attempt.codeLength = outcome.codeLength
                LinkingProgress.Step(attempt.view())
            }

            SignInOutcome.PhoneInvalid -> {
                throw TelegramPhoneInvalid()
            }

            SignInOutcome.PhoneUnregistered -> {
                throw TelegramPhoneUnregistered()
            }

            SignInOutcome.PhoneBanned -> {
                throw TelegramPhoneBanned()
            }

            else -> {
                refuseOrFail(owner, outcome)
            }
        }

    /** TODO(T10): complete the authorized attempt (link, sign in again, or refuse and log out). */
    private fun authorized(outcome: SignInOutcome.Authorized): LinkingProgress =
        LinkingProgress.Authorized(outcome.user)

    /** A wait ends the attempt and tells the Owner when to come back; anything else is a port bug. */
    private fun refuseOrFail(
        owner: OwnerId,
        outcome: SignInOutcome,
    ): Nothing {
        check(outcome is SignInOutcome.WaitRequired) { "Unexpected sign-in outcome ${outcome::class.simpleName}" }
        val retryAt = clock.instant().plusSeconds(outcome.seconds.toLong())
        discard(owner, "flood_wait")
        throw TelegramWaitRequired(retryAt)
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
