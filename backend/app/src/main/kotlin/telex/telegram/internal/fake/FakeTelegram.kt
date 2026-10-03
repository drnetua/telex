package telex.telegram.internal.fake

import org.springframework.context.ApplicationEventPublisher
import telex.shared.Uuid7
import telex.telegram.ChatSnapshot
import telex.telegram.ChatType
import telex.telegram.SessionState
import telex.telegram.SignInOutcome
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.TelegramSessions
import telex.telegram.TelegramUnavailable
import telex.telegram.TelegramUser
import telex.telegram.internal.files.SessionDirectories
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory Telegram scripted by test phone numbers in Telegram's test shape `99966XYYYY`:
 * X picks the behaviour (0 plain, 1 two-step with hint, 2 two-step without hint, 3 banned, 4 unregistered,
 * 5 flood wait on the phone, 6 flood wait on the code, 8 two-step with hint and flood wait on the password,
 * 7 terminate right after link); YYYY is the number of chats (and makes the account unique).
 */
@Suppress("TooManyFunctions") // a port implementation plus its test hooks
class FakeTelegram(
    private val events: ApplicationEventPublisher,
    private val configured: Boolean,
    private val directories: SessionDirectories,
) : TelegramSessions {
    private class Session {
        val sequence = AtomicLong()
        var phone: String? = null
        var authorized = false
        var reachable = true
    }

    private val sessions = ConcurrentHashMap<TelegramSessionId, Session>()
    private val endedWhileStopped = ConcurrentHashMap.newKeySet<TelegramSessionId>()

    override fun configured() = configured

    override fun open(dbKey: ByteArray): TelegramSessionId {
        val id = TelegramSessionId(Uuid7.next())
        sessions[id] = Session()
        Files.createFile(directories.create(id).resolve(MARKER_FILE))
        return id
    }

    override fun reopen(
        id: TelegramSessionId,
        dbKey: ByteArray,
    ) {
        val session = sessions.computeIfAbsent(id) { Session().also { it.authorized = true } }
        directories.create(id)
        if (endedWhileStopped.remove(id)) {
            session.authorized = false
            publishState(id, session, SessionState.Closed)
        } else if (session.authorized) {
            publishState(id, session, SessionState.Ready)
        }
    }

    override fun sendPhone(
        id: TelegramSessionId,
        digits: String,
    ): SignInOutcome {
        val session = reachable(id)
        val scenario = scenarioOf(digits) ?: return SignInOutcome.PhoneInvalid
        return when (scenario) {
            BANNED -> {
                SignInOutcome.PhoneBanned
            }

            UNREGISTERED -> {
                SignInOutcome.PhoneUnregistered
            }

            FLOOD -> {
                SignInOutcome.WaitRequired(FLOOD_WAIT_SECONDS)
            }

            else -> {
                session.phone = digits
                SignInOutcome.CodeSent(CODE.length)
            }
        }
    }

    override fun resendCode(id: TelegramSessionId): SignInOutcome {
        val session = reachable(id)
        check(session.phone != null) { "No phone sent" }
        return SignInOutcome.CodeSent(CODE.length)
    }

    override fun checkCode(
        id: TelegramSessionId,
        code: String,
    ): SignInOutcome {
        val session = reachable(id)
        val phone = checkNotNull(session.phone) { "No phone sent" }
        return when {
            scenarioOf(phone) == FLOOD_CODE -> {
                SignInOutcome.WaitRequired(FLOOD_WAIT_SECONDS)
            }

            code == EXPIRED_CODE -> {
                SignInOutcome.CodeExpired
            }

            code != CODE -> {
                SignInOutcome.CodeWrong
            }

            scenarioOf(phone) == TWO_STEP_HINT || scenarioOf(phone) == FLOOD_PASSWORD -> {
                SignInOutcome.PasswordNeeded(PASSWORD_HINT)
            }

            scenarioOf(phone) == TWO_STEP -> {
                SignInOutcome.PasswordNeeded(null)
            }

            else -> {
                authorize(id, session, phone)
            }
        }
    }

    override fun checkPassword(
        id: TelegramSessionId,
        password: String,
    ): SignInOutcome {
        val session = reachable(id)
        val phone = checkNotNull(session.phone) { "No phone sent" }
        if (scenarioOf(phone) == FLOOD_PASSWORD) return SignInOutcome.WaitRequired(FLOOD_WAIT_SECONDS)
        return if (password == PASSWORD) {
            authorize(id, session, phone)
        } else {
            SignInOutcome.PasswordWrong(PASSWORD_HINT.takeIf { scenarioOf(phone) == TWO_STEP_HINT })
        }
    }

    override fun logOut(
        id: TelegramSessionId,
        timeout: Duration,
    ): Boolean {
        val session = session(id)
        if (!session.reachable) {
            Thread.sleep(timeout.toMillis())
            return false
        }
        session.authorized = false
        publishState(id, session, SessionState.Closed)
        return true
    }

    override fun close(id: TelegramSessionId) {
        sessions.remove(id)
    }

    override fun destroy(id: TelegramSessionId) {
        sessions.remove(id)
        directories.delete(id)
    }

    override fun sweepOrphans(referenced: Set<TelegramSessionId>) = directories.sweepOrphans(referenced)

    /** Test hook: teleX stops. Every client is gone; the session directories stay. */
    fun simulateStop() = sessions.clear()

    /** Test hook: Telegram ended this session while teleX was stopped; the next `reopen` finds it closed. */
    fun endWhileStopped(id: TelegramSessionId) {
        endedWhileStopped.add(id)
    }

    /** Test hook: true while a client is open for [id]. */
    fun isOpen(id: TelegramSessionId) = sessions.containsKey(id)

    /** Test hook: Telegram becomes unreachable. Emits Connecting, never Closed (AC-122). */
    fun dropConnectivity(id: TelegramSessionId) {
        val session = session(id)
        session.reachable = false
        if (session.authorized) publishState(id, session, SessionState.Connecting)
    }

    /** Test hook: Telegram is reachable again. */
    fun restoreConnectivity(id: TelegramSessionId) {
        val session = session(id)
        session.reachable = true
        if (session.authorized) publishState(id, session, SessionState.Ready)
    }

    /** Test hook: Telegram ends the session (the Owner terminated it elsewhere). */
    fun terminate(id: TelegramSessionId) {
        val session = session(id)
        session.authorized = false
        publishState(id, session, SessionState.Closed)
    }

    private fun session(id: TelegramSessionId) = checkNotNull(sessions[id]) { "Unknown session $id" }

    private fun reachable(id: TelegramSessionId): Session {
        val session = session(id)
        if (!session.reachable) throw TelegramUnavailable()
        return session
    }

    private fun scenarioOf(digits: String): Char? =
        digits
            .takeIf {
                it.length == PHONE_LENGTH && it.all(Char::isDigit) && it.startsWith(PHONE_PREFIX)
            }?.get(PHONE_PREFIX.length)

    private fun authorize(
        id: TelegramSessionId,
        session: Session,
        phone: String,
    ): SignInOutcome {
        session.authorized = true
        publishState(id, session, SessionState.Ready)
        val total = phone.takeLast(CHATS_DIGITS).toInt()
        publishChats(id, total)
        if (scenarioOf(phone) == TERMINATE) terminate(id)
        return SignInOutcome.Authorized(
            TelegramUser(
                telegramUserId = phone.toLong(),
                displayName = "Test user ${phone.takeLast(CHATS_DIGITS)}",
                phoneCountryCode = phone.take(COUNTRY_CODE_DIGITS),
                phoneLastTwo = phone.takeLast(2),
            ),
        )
    }

    private fun publishState(
        id: TelegramSessionId,
        session: Session,
        state: SessionState,
    ) {
        events.publishEvent(TelegramSessionStateChanged(id, state, session.sequence.incrementAndGet()))
    }

    private fun publishChats(
        id: TelegramSessionId,
        total: Int,
    ) {
        val chats =
            List(total) {
                ChatSnapshot(
                    chatId = -1_000_000_000_000L - it,
                    type = ChatType.Supergroup,
                    title = "Test chat ${it + 1}",
                    folderIds = emptyList(),
                    archived = false,
                    unreadCount = 0,
                    order = (total - it).toLong(),
                )
            }
        val batches = chats.chunked(BATCH_SIZE).ifEmpty { listOf(emptyList()) }
        batches.forEachIndexed { index, batch ->
            events.publishEvent(TelegramChatsChanged(id, batch, emptyList(), total, index == batches.lastIndex))
        }
    }

    companion object {
        const val CODE = "12345"
        const val EXPIRED_CODE = "00000"
        const val PASSWORD = "secret"
        const val PASSWORD_HINT = "first pet"
        const val FLOOD_WAIT_SECONDS = 30
        const val MARKER_FILE = "fake-session"

        private const val PHONE_PREFIX = "99966"
        private const val PHONE_LENGTH = 10
        private const val CHATS_DIGITS = 4
        private const val COUNTRY_CODE_DIGITS = 2
        private const val BATCH_SIZE = 50
        private const val TWO_STEP_HINT = '1'
        private const val TWO_STEP = '2'
        private const val BANNED = '3'
        private const val UNREGISTERED = '4'
        private const val FLOOD = '5'
        private const val TERMINATE = '7'
        private const val FLOOD_CODE = '6'
        private const val FLOOD_PASSWORD = '8'
    }
}
