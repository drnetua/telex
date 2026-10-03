package telex.telegram.internal.tdlight

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import telex.telegram.ChatType
import telex.telegram.SessionState
import telex.telegram.SignInOutcome
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.TelegramUnavailable
import telex.telegram.internal.files.SessionDirectories
import telex.telegram.tdlib.TdlibChat
import telex.telegram.tdlib.TdlibChatList
import telex.telegram.tdlib.TdlibChatPosition
import telex.telegram.tdlib.TdlibChatType
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class TdlightTelegramSessionsTest {
    @TempDir
    lateinit var root: Path

    private val events = CopyOnWriteArrayList<Any>()
    private val tdlib = ScriptedTdlib()
    private lateinit var sessions: TdlightTelegramSessions
    private val key = ByteArray(32) { 3 }

    private fun create(stepTimeout: Duration = Duration.ofSeconds(5)): TdlightTelegramSessions {
        sessions =
            TdlightTelegramSessions(
                facade = tdlib,
                events = ApplicationEventPublisher { events += it },
                directories = SessionDirectories(root, Clock.systemUTC()),
                apiId = 1,
                apiHash = "hash",
                stepTimeout = stepTimeout,
            )
        return sessions
    }

    @AfterEach
    fun stop() {
        if (this::sessions.isInitialized) sessions.shutdown()
    }

    private fun states() = events.filterIsInstance<TelegramSessionStateChanged>()

    private fun chats() = events.filterIsInstance<TelegramChatsChanged>()

    private fun auth(
        state: String,
        codeLength: Int? = null,
        hint: String? = null,
    ) = TdlibUpdate.AuthorizationState(state, codeLength, hint)

    private fun chat(
        id: Long,
        title: String = "Chat $id",
        order: Long = id,
        list: TdlibChatList = TdlibChatList.Main,
    ) = TdlibChat(id, TdlibChatType.Supergroup, title, 0, listOf(TdlibChatPosition(list, order)))

    /** A number ending 0 is invalid, 1 banned, 2 flood-waited and 3 unregistered; any other signs in. */
    private fun answerOnlyChatLoads() {
        tdlib.respond =
            { _, request -> if (request is TdlibRequest.LoadChats) TdlibResponse.Failure(404, "") else null }
    }

    private fun phoneStep(
        client: ScriptedTdlib.Client,
        request: TdlibRequest.SetPhoneNumber,
    ): TdlibResponse =
        when {
            request.phoneNumber.endsWith("0") -> {
                TdlibResponse.Failure(400, "PHONE_NUMBER_INVALID")
            }

            request.phoneNumber.endsWith("1") -> {
                TdlibResponse.Failure(400, "PHONE_NUMBER_BANNED")
            }

            request.phoneNumber.endsWith("2") -> {
                TdlibResponse.Failure(429, "Too Many Requests: retry after 30")
            }

            else -> {
                val next =
                    if (request.phoneNumber.endsWith(
                            "3",
                        )
                    ) {
                        auth("authorizationStateWaitRegistration")
                    } else {
                        auth("authorizationStateWaitCode", 5)
                    }
                client.emit(next)
                TdlibResponse.Ok("ok")
            }
        }

    private fun scriptSignIn() {
        tdlib.respond = { client, request ->
            when (request) {
                is TdlibRequest.SetPhoneNumber -> {
                    phoneStep(client, request)
                }

                is TdlibRequest.CheckCode -> {
                    when (request.code) {
                        "11111" -> {
                            TdlibResponse.Failure(400, "PHONE_CODE_INVALID")
                        }

                        "22222" -> {
                            TdlibResponse.Failure(400, "PHONE_CODE_EXPIRED")
                        }

                        "33333" -> {
                            client.emit(auth("authorizationStateWaitPassword", hint = "first pet"))
                            TdlibResponse.Ok("ok")
                        }

                        else -> {
                            client.emit(auth("authorizationStateReady"))
                            TdlibResponse.Ok("ok")
                        }
                    }
                }

                is TdlibRequest.CheckPassword -> {
                    if (request.password == "secret") {
                        client.emit(auth("authorizationStateReady"))
                        TdlibResponse.Ok("ok")
                    } else {
                        TdlibResponse.Failure(400, "PASSWORD_HASH_INVALID")
                    }
                }

                TdlibRequest.ResendCode -> {
                    client.emit(auth("authorizationStateWaitCode", codeLength = 6))
                    TdlibResponse.Ok("ok")
                }

                TdlibRequest.GetMe -> {
                    TdlibResponse.Me(777, "Ada", "Lovelace", "380501234567")
                }

                is TdlibRequest.GetCallingCode -> {
                    TdlibResponse.CallingCode("380")
                }

                is TdlibRequest.LoadChats -> {
                    TdlibResponse.Failure(404, "Not Found")
                }

                else -> {
                    TdlibResponse.Ok("ok")
                }
            }
        }
    }

    @Test
    fun `open creates a directory and a client with the key and waits for the phone step`() {
        scriptSignIn()
        val id = create().open(key)

        assertThat(root.resolve(id.value.toString())).isDirectory()
        val config = tdlib.clients.single().config
        assertThat(config.sessionDirectory).isEqualTo(root.resolve(id.value.toString()))
        assertThat(config.databaseKey).isEqualTo(key)
        assertThat(config.apiId).isEqualTo(1)
    }

    @Test
    fun `open fails with TelegramUnavailable when TDLib never reaches the phone step`() {
        tdlib.onOpen = {}

        assertThatThrownBy { create(Duration.ofMillis(200)).open(key) }.isInstanceOf(TelegramUnavailable::class.java)
    }

    @Test
    fun `a phone step maps to its outcome`() {
        scriptSignIn()
        val sessions = create()

        fun outcome(phone: String) = sessions.sendPhone(sessions.open(key), phone)

        assertThat(outcome("380501234564")).isEqualTo(SignInOutcome.CodeSent(5))
        assertThat(outcome("380501234560")).isEqualTo(SignInOutcome.PhoneInvalid)
        assertThat(outcome("380501234561")).isEqualTo(SignInOutcome.PhoneBanned)
        assertThat(outcome("380501234562")).isEqualTo(SignInOutcome.WaitRequired(30))
    }

    @Test
    fun `an unregistered number maps to PhoneUnregistered and closes the client`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)

        assertThat(sessions.sendPhone(id, "380501234563")).isEqualTo(SignInOutcome.PhoneUnregistered)
        assertThat(tdlib.clients.single().closeCalled).isTrue()
        assertThat(states()).isEmpty()
    }

    @Test
    fun `a code step maps to its outcome`() {
        scriptSignIn()
        val sessions = create()

        fun afterCode(code: String): SignInOutcome {
            val id = sessions.open(key)
            sessions.sendPhone(id, "380501234564")
            return sessions.checkCode(id, code)
        }

        assertThat(afterCode("11111")).isEqualTo(SignInOutcome.CodeWrong)
        assertThat(afterCode("22222")).isEqualTo(SignInOutcome.CodeExpired)
        assertThat(afterCode("33333")).isEqualTo(SignInOutcome.PasswordNeeded("first pet"))
    }

    @Test
    fun `resend reports the new code length`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)
        sessions.sendPhone(id, "380501234564")

        assertThat(sessions.resendCode(id)).isEqualTo(SignInOutcome.CodeSent(6))
    }

    @Test
    fun `a password step returns the hint, then authorizes with the phone reduced to code and last two digits`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)
        sessions.sendPhone(id, "380501234564")
        sessions.checkCode(id, "33333")

        assertThat(sessions.checkPassword(id, "nope")).isEqualTo(SignInOutcome.PasswordWrong("first pet"))
        val outcome = sessions.checkPassword(id, "secret") as SignInOutcome.Authorized

        assertThat(outcome.user.telegramUserId).isEqualTo(777)
        assertThat(outcome.user.displayName).isEqualTo("Ada Lovelace")
        assertThat(outcome.user.phoneCountryCode).isEqualTo("380")
        assertThat(outcome.user.phoneLastTwo).isEqualTo("67")
        assertThat(outcome.toString()).doesNotContain("380501234567")
    }

    @Test
    fun `a step Telegram does not answer throws TelegramUnavailable`() {
        scriptSignIn()
        val sessions = create(Duration.ofMillis(200))
        val id = sessions.open(key)
        tdlib.respond = { _, _ -> null }

        assertThatThrownBy { sessions.sendPhone(id, "380501234564") }.isInstanceOf(TelegramUnavailable::class.java)
    }

    @Test
    fun `reopening two stored sessions makes both Ready with increasing sequences and asks the Owner for nothing`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        val sessions = create()
        val first = TelegramSessionId(telex.shared.Uuid7.next())
        val second = TelegramSessionId(telex.shared.Uuid7.next())

        sessions.reopen(first, key)
        sessions.reopen(second, key)

        await().untilAsserted { assertThat(states().map { it.sessionId }).containsExactlyInAnyOrder(first, second) }
        assertThat(states().map { it.state }).containsOnly(SessionState.Ready)
        assertThat(tdlib.clients.flatMap { it.requests }.filter { it !is TdlibRequest.LoadChats }).isEmpty()
    }

    @Test
    fun `connection loss is Connecting and recovery is Ready, never Closed`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        val sessions = create()
        sessions.reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()

        client.emit(TdlibUpdate.ConnectionState("connectionStateConnecting"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateWaitingForNetwork"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateReady"))

        await().untilAsserted {
            assertThat(states().map { it.state })
                .containsExactly(SessionState.Ready, SessionState.Connecting, SessionState.Ready)
        }
        assertThat(states().map { it.sequence }).isSorted().doesNotHaveDuplicates()
    }

    @Test
    fun `a stored session Telegram ended while teleX was stopped is Closed on reopen`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateWaitPhoneNumber")) }
        val id = TelegramSessionId(telex.shared.Uuid7.next())

        create().reopen(id, key)

        await().untilAsserted { assertThat(states().map { it.state }).containsExactly(SessionState.Closed) }
        assertThat(tdlib.clients.single().closeCalled).isTrue()
    }

    @Test
    fun `a session ended by Telegram while running is Closed once`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()

        client.emit(auth("authorizationStateLoggingOut"))
        client.emit(auth("authorizationStateClosing"))
        client.emit(TdlibUpdate.Closed)

        await().untilAsserted {
            assertThat(states().map { it.state }).containsExactly(SessionState.Ready, SessionState.Closed)
        }
    }

    @Test
    fun `closing a session ourselves is not a lost session`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        await().untilAsserted { assertThat(states()).hasSize(1) }

        sessions.close(id)

        assertThat(tdlib.clients.single().closeCalled).isTrue()
        assertThat(states().map { it.state }).containsExactly(SessionState.Ready)
    }

    @Test
    fun `logOut is true when Telegram closes the session in time and false when it does not`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)
        tdlib.respond = { client, request ->
            if (request == TdlibRequest.LogOut) {
                client.emit(auth("authorizationStateLoggingOut"))
                client.emit(TdlibUpdate.Closed)
                TdlibResponse.Ok("ok")
            } else {
                null
            }
        }
        assertThat(sessions.logOut(id, Duration.ofSeconds(2))).isTrue()

        val stuck = sessions.open(key)
        tdlib.respond = { _, _ -> null }
        assertThat(sessions.logOut(stuck, Duration.ofMillis(200))).isFalse()
    }

    @Test
    fun `logOut of a session that is not open is not confirmed (AC-113)`() {
        val sessions = create()

        assertThat(sessions.logOut(TelegramSessionId(telex.shared.Uuid7.next()), Duration.ofMillis(50))).isFalse()
    }

    @Test
    fun `the chat list is loaded in batches and then kept current (AC-121)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var round = 0
        tdlib.respond = { client, request ->
            if (request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main && round++ == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                client.emit(TdlibUpdate.NewChat(chat(2)))
                TdlibResponse.Ok("ok")
            } else if (request is TdlibRequest.LoadChats && request.list == TdlibChatList.Archive && round++ == 2) {
                client.emit(TdlibUpdate.NewChat(chat(3, order = 5, list = TdlibChatList.Archive)))
                TdlibResponse.Ok("ok")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else {
                null
            }
        }
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)

        await().untilAsserted { assertThat(chats().lastOrNull()?.loadCompleted).isTrue() }
        val loaded = chats().last()
        assertThat(chats().take(chats().size - 1).none { it.loadCompleted }).isTrue()
        assertThat(chats().flatMap { it.upserted }.map { it.chatId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        assertThat(chats().flatMap { it.upserted }.single { it.chatId == 3L }.archived).isTrue()
        assertThat(loaded.total).isEqualTo(3)
        assertThat(chats().flatMap { it.upserted }.first().type).isEqualTo(ChatType.Supergroup)

        val client = tdlib.clients.single()
        client.emit(TdlibUpdate.NewChat(chat(4)))
        await().untilAsserted { assertThat(chats().last().total).isEqualTo(4) }
        assertThat(chats().last().upserted.map { it.chatId }).containsExactly(4L)

        client.emit(TdlibUpdate.ChatTitle(4, "Renamed"))
        await().untilAsserted { assertThat(chats().last().upserted.map { it.title }).containsExactly("Renamed") }

        client.emit(TdlibUpdate.ChatUnread(4, 9))
        await().untilAsserted { assertThat(chats().last().upserted.map { it.unreadCount }).containsExactly(9) }

        client.emit(TdlibUpdate.ChatPosition(1, TdlibChatPosition(TdlibChatList.Main, 0)))
        await().untilAsserted { assertThat(chats().last().removedChatIds).containsExactly(1L) }
        assertThat(chats().last().total).isEqualTo(3)

        client.emit(TdlibUpdate.ChatPosition(2, TdlibChatPosition(TdlibChatList.Folder(7), 4)))
        await().untilAsserted {
            assertThat(
                chats()
                    .last()
                    .upserted
                    .single()
                    .folderIds,
            ).containsExactly(7)
        }
    }

    @Test
    fun `a fresh session announces and loads nothing until startSync (AC-01, AC-116)`() {
        answerOnlyChatLoads()
        val sessions = create()
        val id = sessions.open(key)
        val client = tdlib.clients.single()

        client.emit(auth("authorizationStateReady"))
        client.emit(TdlibUpdate.NewChat(chat(1)))
        Thread.sleep(SETTLE_MILLIS)

        assertThat(states()).isEmpty()
        assertThat(chats()).isEmpty()
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>()).isEmpty()

        sessions.startSync(id)

        await().untilAsserted { assertThat(chats().lastOrNull()?.loadCompleted).isTrue() }
        assertThat(states().map { it.state }).containsExactly(SessionState.Ready)
        assertThat(chats().flatMap { it.upserted }.map { it.chatId }).containsExactly(1L)
    }

    @Test
    fun `a session Telegram closed before startSync is announced Closed by it and loads nothing (AC-117)`() {
        answerOnlyChatLoads()
        val sessions = create()
        val id = sessions.open(key)
        val client = tdlib.clients.single()
        client.emit(auth("authorizationStateReady"))
        client.emit(auth("authorizationStateLoggingOut"))
        client.emit(TdlibUpdate.Closed)
        Thread.sleep(SETTLE_MILLIS)
        assertThat(states()).isEmpty()

        sessions.startSync(id)

        await().untilAsserted { assertThat(states().map { it.state }).containsExactly(SessionState.Closed) }
        assertThat(chats()).isEmpty()
    }

    @Test
    fun `the total mid-load is Telegram's total for both lists or null, never the loaded count (AC-116)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var mainLoads = 0
        tdlib.respond = { client, request ->
            val mainLoad = request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main
            val round = if (mainLoad) mainLoads++ else -1
            if (round == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                TdlibResponse.Ok("ok")
            } else if (round == 1) {
                client.emit(TdlibUpdate.ChatCount(TdlibChatList.Main, 5))
                client.emit(TdlibUpdate.ChatCount(TdlibChatList.Archive, 2))
                client.emit(TdlibUpdate.NewChat(chat(2)))
                TdlibResponse.Ok("ok")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else {
                null
            }
        }
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)

        await().untilAsserted { assertThat(chats().lastOrNull()?.loadCompleted).isTrue() }
        val midLoad = chats().filter { !it.loadCompleted }
        assertThat(midLoad.first().total).isNull()
        assertThat(midLoad.last().total).isEqualTo(7)
        assertThat(chats().last().total).isEqualTo(2)
    }

    @Test
    fun `only the event that completes a load lists every chat it found (AC-121)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var round = 0
        tdlib.respond = { client, request ->
            if (request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main && round++ == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                client.emit(TdlibUpdate.NewChat(chat(2)))
                TdlibResponse.Ok("ok")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else {
                null
            }
        }
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)

        await().untilAsserted { assertThat(chats().lastOrNull()?.loadCompleted).isTrue() }
        assertThat(chats().dropLast(1).map { it.loadedChatIds }).containsOnlyNulls()
        assertThat(chats().last().loadedChatIds).containsExactlyInAnyOrder(1L, 2L)
        tdlib.clients
            .single()
            .emit(TdlibUpdate.NewChat(chat(3)))
        await().untilAsserted { assertThat(chats().last().upserted.map { it.chatId }).containsExactly(3L) }
        assertThat(chats().last().loadedChatIds).isNull()
    }

    @Test
    fun `destroy closes the client and deletes the directory`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)

        sessions.destroy(id)

        assertThat(tdlib.clients.single().closeCalled).isTrue()
        assertThat(root.resolve(id.value.toString())).doesNotExist()
    }

    @Test
    fun `configured needs both the api id and the api hash`() {
        assertThat(create().configured()).isTrue()
        val missing =
            TdlightTelegramSessions(tdlib, { events += it }, SessionDirectories(root, Clock.systemUTC()), null, "")
        assertThat(missing.configured()).isFalse()
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
    }
}
