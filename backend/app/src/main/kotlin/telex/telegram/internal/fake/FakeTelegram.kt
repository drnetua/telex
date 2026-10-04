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
 * 7 terminate right after link, 9 terminate a moment after link, once the Linked Account exists);
 * YYYY is the number of chats (and makes the account unique). A number in the shape `99965XYYYY` is unregistered
 * only once its code is checked (Telegram's WaitRegistration), whatever X is. A number in the shape `99964XYYYY`
 * scripts the Linked Account's life after the link: X = 0 makes Telegram unreachable for a while, then reachable
 * again (Reconnecting, then Connected); X = 1 ends the first session of that phone a moment after the link, and the
 * session made by signing in again survives.
 * A number in the shape `99963XYYYY` refuses the code resend: X = 0 as invalid, X = 1 as banned.
 * Like TDLib, an unregistered number closes the client.
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
        var syncStarted = false
        var reachable = true
    }

    private val sessions = ConcurrentHashMap<TelegramSessionId, Session>()
    private val endedWhileStopped = ConcurrentHashMap.newKeySet<TelegramSessionId>()
    private val loggedOut = ConcurrentHashMap.newKeySet<TelegramSessionId>()
    private val destroyed = ConcurrentHashMap.newKeySet<TelegramSessionId>()
    private val terminatedPhones = ConcurrentHashMap.newKeySet<String>()

    /** Test hook: while true, [open] fails as if Telegram never reached the phone step. */
    @Volatile var openUnavailable = false

    /** Test hook: while true, a sign-in Telegram authorizes then fails, as when `GetMe` fails after Ready. */
    @Volatile var failAfterAuthorization = false

    /** Test hook: while true, [reopen] recreates the directory, then fails as if TDLib could not open the client. */
    @Volatile var reopenUnavailable = false

    override fun configured() = configured

    override fun open(dbKey: ByteArray): TelegramSessionId {
        if (openUnavailable) throw TelegramUnavailable()
        val id = TelegramSessionId(Uuid7.next())
        sessions[id] = Session()
        Files.createFile(directories.create(id).resolve(MARKER_FILE))
        return id
    }

    override fun reopen(
        id: TelegramSessionId,
        dbKey: ByteArray,
    ) {
        directories.create(id)
        if (reopenUnavailable) throw TelegramUnavailable()
        // like TDLib opening an empty database after the directory was destroyed: it asks for a phone number
        // (TdlightSession.onWaitPhone), so the real adapter reports Closed and a log out cannot succeed
        val wasDestroyed = destroyed.remove(id)
        val session = sessions.computeIfAbsent(id) { Session().also { it.authorized = !wasDestroyed } }
        session.syncStarted = true
        // like TDLib, a session teleX signed out of asks for a phone number again: the real adapter reports Closed
        val signedOut = session.authorized && loggedOut.contains(id)
        if (wasDestroyed || endedWhileStopped.remove(id) || signedOut) {
            session.authorized = false
            publishState(id, session, SessionState.Closed)
        } else if (session.authorized) {
            publishState(id, session, SessionState.Ready)
        }
    }

    override fun startSync(id: TelegramSessionId) {
        val session = sessions[id] ?: return
        val phone = session.phone
        if (!session.authorized || phone == null || session.syncStarted) return
        session.syncStarted = true
        publishState(id, session, SessionState.Ready)
        publishChats(id, phone.takeLast(CHATS_DIGITS).toInt())
        when (scenarioOf(phone)) {
            TERMINATE -> terminate(id)
            TERMINATE_LATER -> terminateLater(id)
            TERMINATE_ONCE -> if (terminatedPhones.add(phone)) terminateLater(id)
            OUTAGE -> outageLater(id)
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
                sessions.remove(id)
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
        val phone = checkNotNull(session.phone) { "No phone sent" }
        return when (scenarioOf(phone)) {
            INVALID_ON_RESEND -> SignInOutcome.PhoneInvalid
            BANNED_ON_RESEND -> SignInOutcome.PhoneBanned
            else -> SignInOutcome.CodeSent(CODE.length)
        }
    }

    override fun checkCode(
        id: TelegramSessionId,
        code: String,
    ): SignInOutcome {
        val session = reachable(id)
        val phone = checkNotNull(session.phone) { "No phone sent" }
        return when {
            scenarioOf(phone) == UNREGISTERED_AFTER_CODE -> {
                sessions.remove(id)
                SignInOutcome.PhoneUnregistered
            }

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
                authorize(session, phone)
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
            authorize(session, phone)
        } else {
            SignInOutcome.PasswordWrong(PASSWORD_HINT.takeIf { scenarioOf(phone) == TWO_STEP_HINT })
        }
    }

    override fun authorized(id: TelegramSessionId) = sessions[id]?.authorized == true

    override fun logOut(
        id: TelegramSessionId,
        timeout: Duration,
    ): Boolean {
        val session = sessions[id]?.takeIf { it.authorized } ?: return false
        if (session.reachable) {
            // like TDLib, teleX's own log out is not a lost session: no Closed is announced (AC-113, AC-122)
            session.authorized = false
            loggedOut.add(id)
        } else {
            Thread.sleep(timeout.toMillis())
        }
        return session.reachable
    }

    override fun close(id: TelegramSessionId) {
        sessions.remove(id)
    }

    override fun destroy(id: TelegramSessionId) {
        sessions.remove(id)
        destroyed.add(id)
        directories.delete(id)
    }

    override fun sweepOrphans(referenced: Set<TelegramSessionId>) = directories.sweepOrphans(referenced)

    /** Test hook: teleX stops. Every client is gone; the session directories stay. */
    fun simulateStop() = sessions.clear()

    /** Test hook: Telegram ended this session while teleX was stopped; the next `reopen` finds it closed. */
    fun endWhileStopped(id: TelegramSessionId) {
        endedWhileStopped.add(id)
    }

    /** Test hook: true once Telegram confirmed a log out of [id], so no device of it remains in the account. */
    fun wasLoggedOut(id: TelegramSessionId) = loggedOut.contains(id)

    /** Test hook: how many sessions Telegram confirmed a log out of, to tell that an HTTP refusal logged one out. */
    fun loggedOutCount() = loggedOut.size

    override fun isOpen(id: TelegramSessionId) = sessions.containsKey(id)

    /** Test hook: Telegram pushes [chats] for [id], only while that session is open and authorized. */
    fun pushChats(
        id: TelegramSessionId,
        chats: List<ChatSnapshot>,
    ) {
        if (sessions[id]?.authorized != true) return
        events.publishEvent(TelegramChatsChanged(id, chats, emptyList(), chats.size, true, null))
    }

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

    /** Ends the session after the sign-in has been completed, as Telegram does when the Owner ends it elsewhere. */
    private fun terminateLater(id: TelegramSessionId) {
        Thread
            .ofVirtual()
            .start {
                Thread.sleep(TERMINATE_LATER_DELAY_MILLIS)
                if (sessions.containsKey(id)) terminate(id)
            }
    }

    /** Makes Telegram unreachable for a while after the sync started, then reachable again (AC-122). */
    private fun outageLater(id: TelegramSessionId) {
        Thread
            .ofVirtual()
            .start {
                Thread.sleep(OUTAGE_DELAY_MILLIS)
                if (sessions.containsKey(id)) dropConnectivity(id)
                Thread.sleep(OUTAGE_MILLIS)
                if (sessions.containsKey(id)) restoreConnectivity(id)
            }
    }

    private fun session(id: TelegramSessionId) = checkNotNull(sessions[id]) { "Unknown session $id" }

    private fun reachable(id: TelegramSessionId): Session {
        val session = session(id)
        if (!session.reachable) throw TelegramUnavailable()
        return session
    }

    private fun scenarioOf(digits: String): Char? =
        digits
            .takeIf { it.length == PHONE_LENGTH && it.all(Char::isDigit) }
            ?.let {
                when {
                    it.startsWith(PHONE_PREFIX) -> it[PHONE_PREFIX.length]
                    it.startsWith(UNREGISTERED_AFTER_CODE_PREFIX) -> UNREGISTERED_AFTER_CODE
                    it.startsWith(RESEND_PREFIX) -> resendScenario(it[RESEND_PREFIX.length])
                    it.startsWith(LIFE_PREFIX) -> lifeScenario(it[LIFE_PREFIX.length])
                    else -> null
                }
            }

    private fun resendScenario(digit: Char): Char? =
        when (digit) {
            '0' -> INVALID_ON_RESEND
            '1' -> BANNED_ON_RESEND
            else -> null
        }

    private fun lifeScenario(digit: Char): Char? =
        when (digit) {
            '0' -> OUTAGE
            '1' -> TERMINATE_ONCE
            else -> null
        }

    private fun authorize(
        session: Session,
        phone: String,
    ): SignInOutcome {
        session.authorized = true
        if (failAfterAuthorization) throw TelegramUnavailable()
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
            val last = index == batches.lastIndex
            val loaded = if (last) chats.map { it.chatId }.toSet() else null
            events.publishEvent(TelegramChatsChanged(id, batch, emptyList(), total, last, loaded))
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
        private const val UNREGISTERED_AFTER_CODE_PREFIX = "99965"
        private const val UNREGISTERED_AFTER_CODE = 'u'
        private const val RESEND_PREFIX = "99963"
        private const val INVALID_ON_RESEND = 'i'
        private const val BANNED_ON_RESEND = 'b'
        private const val LIFE_PREFIX = "99964"
        private const val OUTAGE = 'o'
        private const val TERMINATE_ONCE = 'r'
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
        private const val TERMINATE_LATER = '9'
        private const val TERMINATE_LATER_DELAY_MILLIS = 1_500L
        private const val OUTAGE_DELAY_MILLIS = 1_500L
        private const val OUTAGE_MILLIS = 6_000L
    }
}
