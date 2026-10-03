package telex.messaging

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.messaging.internal.account.Completion
import telex.messaging.internal.account.LinkCompletion
import telex.messaging.internal.account.LinkRefusal
import telex.messaging.internal.account.LinkedAccountRows
import telex.messaging.internal.attempt.LinkingAttempt
import telex.messaging.internal.attempt.LinkingAttempts
import telex.messaging.internal.config.AccountLimit
import telex.shared.DomainProblem
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUnavailable
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

/** How an authorized attempt ended well: a new Linked Account, or a Session lost one signed in again. */
enum class LinkingOutcome { LINKED, SIGNED_IN_AGAIN }

/** The answer to a wizard step: the attempt moved (or stayed) at a step, or Telegram authorized the account. */
sealed interface LinkingProgress {
    data class Step(
        val attempt: LinkingAttemptView,
    ) : LinkingProgress

    /** Telegram authorized the attempt and teleX accepted it; the attempt is gone. */
    data class Completed(
        val outcome: LinkingOutcome,
        val linkedAccountId: LinkedAccountId,
        val origin: LinkingOrigin,
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
    private val completion: LinkCompletion,
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

    /** Discards the Owner's open attempt when it signs in again to [account] (the account is being unlinked). */
    fun discardTargeting(
        owner: OwnerId,
        account: LinkedAccountId,
    ) {
        attempts.locked(owner) {
            if (attempts.find(owner)?.target == account) discard(owner, "cancelled")
        }
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
                    authorized(owner, attempt, sessions, outcome)
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
                is SignInOutcome.Authorized -> authorized(owner, attempt, sessions, outcome)
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

    /**
     * Telegram authorized the attempt: it becomes a Linked Account (or signs one in again), or is logged out and
     * destroyed in the same call so no teleX device is left behind (AC-04, AC-108, AC-115, AC-117).
     */
    private fun authorized(
        owner: OwnerId,
        attempt: LinkingAttempt,
        sessions: TelegramSessions,
        outcome: SignInOutcome.Authorized,
    ): LinkingProgress {
        val result =
            try {
                completion.complete(owner, attempt.target, attempt.sessionId, attempt.dbKey, outcome.user)
            } catch (_: DuplicateKeyException) {
                completion.afterRace(owner, outcome.user)
            }
        return when (result) {
            is Completion.Refused -> {
                sessions.logOut(attempt.sessionId, LOG_OUT_TIMEOUT)
                discard(owner, "refused_" + result.reason.name.lowercase())
                throw refusal(result.reason)
            }

            is Completion.Linked -> {
                finish(owner, attempt, "linked")
                LinkingProgress.Completed(LinkingOutcome.LINKED, result.id, attempt.origin)
            }

            is Completion.SignedInAgain -> {
                finish(owner, attempt, "signed_in_again")
                result.replaced?.let {
                    sessions.close(it)
                    sessions.destroy(it)
                }
                LinkingProgress.Completed(LinkingOutcome.SIGNED_IN_AGAIN, result.id, attempt.origin)
            }
        }
    }

    /** The attempt succeeded: it is forgotten and its key wiped, but its Telegram session lives on. */
    private fun finish(
        owner: OwnerId,
        attempt: LinkingAttempt,
        outcome: String,
    ) {
        attempts.remove(owner)
        attempt.wipeKey()
        meters.counter("telex.linking.attempts", "outcome", outcome).increment()
    }

    private fun refusal(reason: LinkRefusal): DomainProblem =
        when (reason) {
            LinkRefusal.OTHER_OWNER -> TelegramAccountOwnedByAnotherOwner()
            LinkRefusal.ALREADY_LINKED -> TelegramAccountAlreadyLinked()
            LinkRefusal.LIMIT -> LinkedAccountLimitReached(limit.maxPerOwner)
            LinkRefusal.MISMATCH -> TelegramAccountMismatch()
        }

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
        val LOG_OUT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val IDLE_LIMIT: Duration = Duration.ofMinutes(15)
    }
}
