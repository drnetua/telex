package telex.telegram.internal.tdlight

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import telex.shared.Uuid7
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUnavailable
import telex.telegram.TelegramUser
import telex.telegram.internal.files.SessionDirectories
import telex.telegram.tdlib.TdlibClientConfig
import telex.telegram.tdlib.TdlibFacade
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The `tdlight` adapter of the `telegram` port: one TDLib client per session directory, driven through the
 * [TdlibFacade] only (ADR-0004). TDLib's callbacks are handed to a virtual thread per session, in order.
 *
 * Never logged: phone numbers, codes, passwords, hints, keys, names, chat titles or raw TDLib objects.
 */
@Suppress("LongParameterList", "TooManyFunctions") // a port implementation; the config is injected
class TdlightTelegramSessions(
    private val facade: TdlibFacade,
    private val events: ApplicationEventPublisher,
    private val directories: SessionDirectories,
    private val apiId: Int?,
    private val apiHash: String?,
    private val useTestDc: Boolean = false,
    private val stepTimeout: Duration = Duration.ofSeconds(STEP_TIMEOUT_SECONDS),
) : TelegramSessions {
    private val sessions = ConcurrentHashMap<TelegramSessionId, TdlightSession>()

    /** Reopens in flight: a session enters [sessions] only once the facade opened it, so [logOut] waits on these. */
    private val opening = ConcurrentHashMap<TelegramSessionId, CompletableFuture<Unit>>()

    override fun configured() = apiId != null && !apiHash.isNullOrBlank()

    override fun open(dbKey: ByteArray): TelegramSessionId {
        val id = TelegramSessionId(Uuid7.next())
        directories.create(id)
        val session = start(id, dbKey, reopened = false)
        try {
            session.waitingForPhone.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            destroy(id)
            throw TelegramUnavailable().apply { initCause(e) }
        } catch (e: ExecutionException) {
            destroy(id)
            throw TelegramUnavailable().apply { initCause(e) }
        }
        return id
    }

    override fun reopen(
        id: TelegramSessionId,
        dbKey: ByteArray,
    ) {
        // Claim first, then check: a reopen that finished registered its session before releasing its claim,
        // so a racing caller (boot, an unlink) either loses the claim or sees the session, never opens a second one.
        val inFlight = CompletableFuture<Unit>()
        if (opening.putIfAbsent(id, inFlight) != null) return
        try {
            if (sessions.containsKey(id)) return
            directories.create(id)
            start(id, dbKey, reopened = true)
        } finally {
            opening.remove(id, inFlight)
            inFlight.complete(Unit)
        }
    }

    override fun isOpen(id: TelegramSessionId) = sessions.containsKey(id) || opening.containsKey(id)

    override fun startSync(id: TelegramSessionId) {
        sessions[id]?.startSync()
    }

    override fun sendPhone(
        id: TelegramSessionId,
        digits: String,
    ): SignInOutcome {
        val session = session(id)
        return when (val result = step(session, TdlibRequest.SetPhoneNumber(digits))) {
            is StepResult.Error -> mapPhoneError(result.failure)
            is StepResult.State -> afterState(session, result.update)
        }
    }

    override fun resendCode(id: TelegramSessionId): SignInOutcome {
        val session = session(id)
        return when (val result = step(session, TdlibRequest.ResendCode)) {
            is StepResult.Error -> mapPhoneError(result.failure)
            is StepResult.State -> afterState(session, result.update)
        }
    }

    override fun checkCode(
        id: TelegramSessionId,
        code: String,
    ): SignInOutcome {
        val session = session(id)
        return when (val result = step(session, TdlibRequest.CheckCode(code))) {
            is StepResult.Error -> mapCodeError(result.failure)
            is StepResult.State -> afterState(session, result.update)
        }
    }

    override fun checkPassword(
        id: TelegramSessionId,
        password: String,
    ): SignInOutcome {
        val session = session(id)
        return when (val result = step(session, TdlibRequest.CheckPassword(password))) {
            is StepResult.Error -> mapPasswordError(session, result.failure)
            is StepResult.State -> afterState(session, result.update)
        }
    }

    override fun authorized(id: TelegramSessionId) = sessions[id]?.isAuthorized() == true

    override fun logOut(
        id: TelegramSessionId,
        timeout: Duration,
    ): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        awaitReopen(id, timeout)
        // an ended session never confirms; a reopened one that is not Ready yet is waited for, a fresh one is not
        val session = sessions[id]?.takeUnless { it.isOver() }
        val signedIn =
            session != null &&
                (
                    session.isAuthorized() ||
                        (
                            session.wasReopened() &&
                                session.awaitAuthorized(Duration.ofNanos(deadline - System.nanoTime()))
                        )
                )
        if (session == null || !signedIn || !session.beginLogOut()) return false
        session.client().send(TdlibRequest.LogOut)
        // only TDLib closing the session through this log out confirms it; teleX's own close does not
        var confirmed = false
        try {
            confirmed = session.loggedOut.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            // not finished in time: not confirmed
        } finally {
            // whatever ended the wait (a timeout, an interrupt), a Closed that comes later is a lost session again
            if (!confirmed) session.abandonLogOut()
        }
        return confirmed
    }

    /** Waits up to [timeout] for a reopen of [id] that is in flight, so an unlink during it still signs out. */
    private fun awaitReopen(
        id: TelegramSessionId,
        timeout: Duration,
    ) {
        val inFlight = opening[id] ?: return
        try {
            inFlight.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // not signed in time: the log out below finds no session and is not confirmed
        }
    }

    override fun close(id: TelegramSessionId) {
        val session = sessions.remove(id) ?: return
        release(session)
    }

    override fun destroy(id: TelegramSessionId) {
        close(id)
        directories.delete(id)
    }

    override fun sweepOrphans(referenced: Set<TelegramSessionId>) = directories.sweepOrphans(referenced)

    /** Test hook: true while a [logOut] of [id] waits for the reopened session to reach Ready. */
    internal fun awaitingAuthorization(id: TelegramSessionId) = sessions[id]?.isAwaitingAuthorization() == true

    /** Closes every client and waits for TDLib to finish, so the JVM does not exit under a live client. */
    fun shutdown() {
        sessions.keys.toList().forEach(::close)
    }

    private fun start(
        id: TelegramSessionId,
        dbKey: ByteArray,
        reopened: Boolean,
    ): TdlightSession {
        check(configured()) { "Telegram api id and hash are not configured" }
        val session = TdlightSession(id, reopened, events)
        val config =
            TdlibClientConfig(directories.create(id), dbKey, checkNotNull(apiId), checkNotNull(apiHash), useTestDc)
        var opened = false
        try {
            session.attach(facade.open(config) { update -> session.onUpdate(update) })
            opened = true
        } finally {
            if (!opened) {
                session.dispose()
                if (!reopened) directories.delete(id)
            }
        }
        sessions[id] = session
        return session
    }

    private fun session(id: TelegramSessionId) = checkNotNull(sessions[id]) { "Unknown session $id" }

    private fun release(session: TdlightSession) {
        session.markClosing()
        try {
            session.clientIfAttached()?.close()
            session.closed.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            log.warn("TDLib client {} did not report closed in time", session.id.value)
        } catch (_: InterruptedException) {
            // the caller's work is done (an unlink already deleted the account); keep the flag, don't fail it
            Thread.currentThread().interrupt()
            log.warn("Interrupted while TDLib client {} closed", session.id.value)
        } finally {
            session.dispose()
        }
    }

    /** Sends [request] and waits for the next authorization state or an error answer. */
    private fun step(
        session: TdlightSession,
        request: TdlibRequest,
    ): StepResult {
        val waiter = CompletableFuture<StepResult>()
        session.waiter = waiter
        try {
            session.client().send(request).whenComplete { response, error ->
                when {
                    error != null -> waiter.completeExceptionally(error)
                    response is TdlibResponse.Failure -> waiter.complete(StepResult.Error(response))
                }
            }
            return waiter.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw TelegramUnavailable().apply { initCause(e) }
        } catch (e: ExecutionException) {
            throw TelegramUnavailable().apply { initCause(e) }
        } finally {
            session.waiter = null
        }
    }

    private fun afterState(
        session: TdlightSession,
        update: TdlibUpdate.AuthorizationState,
    ): SignInOutcome =
        when (update.state) {
            STATE_WAIT_CODE -> {
                SignInOutcome.CodeSent(update.codeLength ?: DEFAULT_CODE_LENGTH)
            }

            STATE_WAIT_PASSWORD -> {
                SignInOutcome.PasswordNeeded(update.passwordHint)
            }

            STATE_WAIT_REGISTRATION -> {
                session.markClosing()
                session.client().close()
                SignInOutcome.PhoneUnregistered
            }

            STATE_READY -> {
                SignInOutcome.Authorized(user(session))
            }

            else -> {
                throw TelegramUnavailable()
            }
        }

    private fun user(session: TdlightSession): TelegramUser {
        val me = request<TdlibResponse.Me>(session, TdlibRequest.GetMe)
        val callingCode = request<TdlibResponse.CallingCode>(session, TdlibRequest.GetCallingCode(me.phoneNumber))
        return TelegramUser(
            telegramUserId = me.userId,
            displayName = listOf(me.firstName, me.lastName).filter(String::isNotBlank).joinToString(" "),
            phoneCountryCode = callingCode.code,
            phoneLastTwo = me.phoneNumber.filter(Char::isDigit).takeLast(2),
        )
    }

    private inline fun <reified T : TdlibResponse> request(
        session: TdlightSession,
        request: TdlibRequest,
    ): T {
        val response =
            try {
                session.client().send(request).get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                null
            } catch (_: ExecutionException) {
                null
            }
        return response as? T ?: throw TelegramUnavailable()
    }

    private fun mapPhoneError(failure: TdlibResponse.Failure): SignInOutcome =
        mapFlood(failure)
            ?: when {
                failure.message.contains("PHONE_NUMBER_INVALID") -> SignInOutcome.PhoneInvalid
                failure.message.contains("PHONE_NUMBER_BANNED") -> SignInOutcome.PhoneBanned
                failure.message.contains("PHONE_NUMBER_UNOCCUPIED") -> SignInOutcome.PhoneUnregistered
                else -> throw TelegramUnavailable()
            }

    private fun mapCodeError(failure: TdlibResponse.Failure): SignInOutcome =
        mapFlood(failure)
            ?: when {
                failure.message.contains("PHONE_CODE_EXPIRED") -> {
                    SignInOutcome.CodeExpired
                }

                failure.message.contains("PHONE_CODE_INVALID") || failure.message.contains("PHONE_CODE_EMPTY") -> {
                    SignInOutcome.CodeWrong
                }

                else -> {
                    mapPhoneError(failure)
                }
            }

    private fun mapPasswordError(
        session: TdlightSession,
        failure: TdlibResponse.Failure,
    ): SignInOutcome =
        mapFlood(failure)
            ?: if (failure.message.contains("PASSWORD_HASH_INVALID")) {
                SignInOutcome.PasswordWrong(session.passwordHint)
            } else {
                throw TelegramUnavailable()
            }

    /** Flood wait ("Too Many Requests: retry after N" or `FLOOD_WAIT_N`); teleX adds no retries. */
    private fun mapFlood(failure: TdlibResponse.Failure): SignInOutcome? {
        val seconds =
            FLOOD_SECONDS
                .find(failure.message)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        return when {
            seconds != null -> SignInOutcome.WaitRequired(seconds)
            failure.code == HTTP_TOO_MANY_REQUESTS -> throw TelegramUnavailable()
            else -> null
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(TdlightTelegramSessions::class.java)
        val FLOOD_SECONDS = Regex("""(?:retry after|FLOOD_WAIT_)\s*(\d+)""")

        const val STEP_TIMEOUT_SECONDS = 8L
        const val DEFAULT_CODE_LENGTH = 5
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val STATE_WAIT_CODE = "authorizationStateWaitCode"
        const val STATE_WAIT_PASSWORD = "authorizationStateWaitPassword"
        const val STATE_WAIT_REGISTRATION = "authorizationStateWaitRegistration"
        const val STATE_READY = "authorizationStateReady"
    }
}
