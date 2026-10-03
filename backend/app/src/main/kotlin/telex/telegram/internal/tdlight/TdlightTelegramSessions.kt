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
        if (sessions.containsKey(id)) return
        directories.create(id)
        start(id, dbKey, reopened = true)
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
            is StepResult.Error -> mapFlood(result.failure) ?: throw TelegramUnavailable()
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

    override fun logOut(
        id: TelegramSessionId,
        timeout: Duration,
    ): Boolean {
        val session = sessions[id] ?: return true
        session.client().send(TdlibRequest.LogOut)
        return try {
            session.closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            true
        } catch (_: TimeoutException) {
            false
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
        sessions[id] = session
        val config =
            TdlibClientConfig(directories.create(id), dbKey, checkNotNull(apiId), checkNotNull(apiHash), useTestDc)
        session.attach(facade.open(config) { update -> session.onUpdate(update) })
        return session
    }

    private fun session(id: TelegramSessionId) = checkNotNull(sessions[id]) { "Unknown session $id" }

    private fun release(session: TdlightSession) {
        session.markClosing()
        try {
            session.client().close()
            session.closed.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            log.warn("TDLib client {} did not report closed in time", session.id.value)
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
                    throw TelegramUnavailable()
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
