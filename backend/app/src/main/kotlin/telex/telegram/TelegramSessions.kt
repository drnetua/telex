package telex.telegram

import telex.shared.TypedId
import java.time.Duration
import java.util.UUID

/** Identifies one Telegram session (one TDLib database directory). `telegram`'s own id; no Owner id enters here. */
@JvmInline value class TelegramSessionId(
    override val value: UUID,
) : TypedId

/** A step Telegram did not answer in time, or could not be reached for (maps to 503 `telegram-unavailable`). */
class TelegramUnavailable(
    message: String = "Telegram is unreachable",
) : RuntimeException(message)

/** The Telegram user a sign-in authorized. The full phone number never leaves `telegram`. */
data class TelegramUser(
    val telegramUserId: Long,
    val displayName: String,
    val phoneCountryCode: String,
    val phoneLastTwo: String,
)

/** The answer to one sign-in step. Each step uses the subset documented on the [TelegramSessions] method. */
sealed interface SignInOutcome {
    data class CodeSent(
        val codeLength: Int,
    ) : SignInOutcome

    data object PhoneInvalid : SignInOutcome

    data object PhoneUnregistered : SignInOutcome

    data object PhoneBanned : SignInOutcome

    data class WaitRequired(
        val seconds: Int,
    ) : SignInOutcome

    data class PasswordNeeded(
        val hint: String?,
    ) : SignInOutcome

    data class PasswordWrong(
        val hint: String?,
    ) : SignInOutcome

    data object CodeWrong : SignInOutcome

    data object CodeExpired : SignInOutcome

    data class Authorized(
        val user: TelegramUser,
    ) : SignInOutcome
}

/**
 * The `telegram` port: open, sign in, log out, close and destroy a session. Adapters are chosen by
 * `telex.telegram.adapter` (`tdlight` default, `fake`). A step Telegram does not answer throws [TelegramUnavailable].
 */
@Suppress("TooManyFunctions") // the whole session lifecycle is one port
interface TelegramSessions {
    /** True when the api id and hash are present (AC-119). */
    fun configured(): Boolean

    fun open(dbKey: ByteArray): TelegramSessionId

    fun reopen(
        id: TelegramSessionId,
        dbKey: ByteArray,
    )

    /** Returns [SignInOutcome.CodeSent], [SignInOutcome.PhoneInvalid], [SignInOutcome.PhoneUnregistered],
     *  [SignInOutcome.PhoneBanned] or [SignInOutcome.WaitRequired]. */
    fun sendPhone(
        id: TelegramSessionId,
        digits: String,
    ): SignInOutcome

    fun resendCode(id: TelegramSessionId): SignInOutcome

    /** Returns [SignInOutcome.PasswordNeeded], [SignInOutcome.Authorized], [SignInOutcome.CodeWrong],
     *  [SignInOutcome.CodeExpired] or [SignInOutcome.WaitRequired]. */
    fun checkCode(
        id: TelegramSessionId,
        code: String,
    ): SignInOutcome

    /** Returns [SignInOutcome.Authorized], [SignInOutcome.PasswordWrong] or [SignInOutcome.WaitRequired]. */
    fun checkPassword(
        id: TelegramSessionId,
        password: String,
    ): SignInOutcome

    /**
     * Starts the chat list sync and announces the session state. Called once the Linked Account row is committed
     * (link, or sign in again), because the events of a session no account holds yet are dropped (AC-01, AC-116).
     * A reopened session starts on its own.
     */
    fun startSync(id: TelegramSessionId)

    /** True while Telegram has authorized the session and it is still open: closing it alone would leave a device. */
    fun authorized(id: TelegramSessionId): Boolean

    /**
     * True when Telegram confirmed the log out within [timeout]; the whole call is bounded by it. A reopened session
     * that is not Ready yet is waited for, then signed out. A fresh session that never signed in, and any ended one
     * (lost, closing or closed), is never confirmed and gets no LogOut; neither is a session that is not open.
     * Confirmed means Telegram itself finished the log out (TDLib closed the session through it); a session teleX
     * closes or releases before that is not confirmed. teleX's own log out is not a lost session: it announces no
     * [SessionState.Closed], so the account is never shown as Session lost on its way out (AC-113, AC-122). The
     * caller still closes and destroys the session afterwards.
     */
    fun logOut(
        id: TelegramSessionId,
        timeout: Duration,
    ): Boolean

    fun close(id: TelegramSessionId)

    /** Deletes the session's directory; a failed deletion is retried every minute. */
    fun destroy(id: TelegramSessionId)

    /** Deletes every session directory whose id is not in [referenced] (the caller's live sessions). */
    fun sweepOrphans(referenced: Set<TelegramSessionId>)
}
