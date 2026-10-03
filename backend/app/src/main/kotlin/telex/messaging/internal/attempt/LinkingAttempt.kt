package telex.messaging.internal.attempt

import telex.identity.SignInSessionId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkingOrigin
import telex.messaging.LinkingStep
import telex.telegram.TelegramSessionId
import java.time.Instant

/**
 * One Owner's open linking attempt, held in memory only (sad §8). The TDLib database key lives here until the
 * account is created and is never persisted; a discarded attempt has its key wiped.
 */
class LinkingAttempt(
    val sessionId: TelegramSessionId,
    val dbKey: ByteArray,
    val origin: LinkingOrigin,
    val target: LinkedAccountId?,
    @Volatile var signInSession: SignInSessionId,
    @Volatile var lastStepAt: Instant,
) {
    @Volatile var step: LinkingStep = LinkingStep.PHONE

    @Volatile var codeLength: Int? = null

    @Volatile var passwordHint: String? = null

    /** Records a step by [by] at [now]: resets the inactivity timer and remembers who stepped. */
    fun stepped(
        by: SignInSessionId,
        now: Instant,
    ) {
        signInSession = by
        lastStepAt = now
    }

    fun wipeKey() = dbKey.fill(0)

    // The key must not leak through logs.
    override fun toString(): String = "LinkingAttempt(step=$step, origin=$origin)"
}
