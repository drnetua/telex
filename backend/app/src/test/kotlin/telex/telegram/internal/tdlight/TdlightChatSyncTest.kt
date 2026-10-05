package telex.telegram.internal.tdlight

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import telex.telegram.ChatType
import telex.telegram.SessionState
import telex.telegram.TelegramChatsChanged
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
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

/** How a session loads its chat list and keeps it current: batches, totals, startSync, resuming after an outage. */
class TdlightChatSyncTest {
    @TempDir
    lateinit var root: Path

    private val events = CopyOnWriteArrayList<Any>()
    private val tdlib = ScriptedTdlib()
    private lateinit var sessions: TdlightTelegramSessions
    private val key = ByteArray(32) { 3 }

    private fun create(): TdlightTelegramSessions {
        sessions =
            TdlightTelegramSessions(
                facade = tdlib,
                events = ApplicationEventPublisher { events += it },
                directories = SessionDirectories(root, Clock.systemUTC()),
                apiId = 1,
                apiHash = "hash",
                stepTimeout = Duration.ofSeconds(5),
            )
        return sessions
    }

    @AfterEach
    fun stop() {
        if (this::sessions.isInitialized) sessions.shutdown()
    }

    private fun states() = events.filterIsInstance<TelegramSessionStateChanged>()

    private fun chats() = events.filterIsInstance<TelegramChatsChanged>()

    private fun auth(state: String) = TdlibUpdate.AuthorizationState(state, null, null)

    private fun chat(
        id: Long,
        title: String = "Chat $id",
        order: Long = id,
        list: TdlibChatList = TdlibChatList.Main,
    ) = TdlibChat(id, TdlibChatType.Supergroup, title, 0, listOf(TdlibChatPosition(list, order)))

    private fun answerOnlyChatLoads() {
        tdlib.respond =
            { _, request -> if (request is TdlibRequest.LoadChats) TdlibResponse.Failure(404, "") else null }
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
        client.emit(TdlibUpdate.ConnectionState("connectionStateUpdating"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateReady"))
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
                client.emit(TdlibUpdate.ChatCount(TdlibChatList.Folder(7), 4))
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
    fun `a chat-list load that failed starts again on the next connection Ready (AC-116, AC-121, AC-122, AC-36)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var mainLoads = 0
        tdlib.respond = { client, request ->
            val mainLoad = request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main
            if (mainLoad && mainLoads++ == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                client.emit(TdlibUpdate.NewChat(chat(2)))
                TdlibResponse.Failure(500, "Request aborted")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else {
                null
            }
        }
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()
        await().untilAsserted { assertThat(chats()).isNotEmpty() }
        Thread.sleep(SETTLE_MILLIS)
        assertThat(chats().none { it.loadCompleted }).isTrue()

        client.emit(TdlibUpdate.ConnectionState("connectionStateConnecting"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateReady"))

        await().untilAsserted { assertThat(chats().lastOrNull()?.loadCompleted).isTrue() }
        assertThat(chats().last().total).isEqualTo(2)
        assertThat(chats().last().loadedChatIds).containsExactlyInAnyOrder(1L, 2L)
        client.emit(TdlibUpdate.ChatTitle(2, "Renamed"))
        await().untilAsserted { assertThat(chats().last().upserted.map { it.title }).containsExactly("Renamed") }

        client.emit(TdlibUpdate.ConnectionState("connectionStateConnecting"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateReady"))
        Thread.sleep(SETTLE_MILLIS)
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>().count { it.list == TdlibChatList.Main })
            .isEqualTo(2)
    }

    /** The first main-list load fails with a 500, later ones complete; LogOut never finishes. */
    private fun failFirstLoad() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var mainLoads = 0
        tdlib.respond = { client, request ->
            val mainLoad = request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main
            if (mainLoad && mainLoads++ == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                TdlibResponse.Failure(500, "Request aborted")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else if (request == TdlibRequest.LogOut) {
                client.emit(auth("authorizationStateLoggingOut"))
                TdlibResponse.Ok("ok")
            } else {
                null
            }
        }
    }

    /** Reconnects [client]: Connecting, then [state]. */
    private fun reconnect(
        client: ScriptedTdlib.Client,
        state: String = "connectionStateReady",
    ) {
        client.emit(TdlibUpdate.ConnectionState("connectionStateConnecting"))
        client.emit(TdlibUpdate.ConnectionState(state))
    }

    @Test
    fun `a failed chat-list load is resumed by connectionStateUpdating alone, before any backoff (AC-122, AC-116)`() {
        failFirstLoad()
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()
        await().until { mainLoads(client) == 1 }
        Thread.sleep(SETTLE_MILLIS)

        reconnect(client, "connectionStateUpdating")

        await().atMost(Duration.ofMillis(BEFORE_BACKOFF_MILLIS)).untilAsserted {
            assertThat(chats().lastOrNull()?.loadCompleted).isTrue()
        }
        assertThat(chats().last().loadedChatIds).containsExactly(1L)
    }

    @Test
    fun `a failed chat-list load is not restarted by a Ready that comes after the session closed (AC-116)`() {
        failFirstLoad()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        await().until { mainLoads(client) == 1 }

        sessions.close(id)
        reconnect(client)

        Thread.sleep(SETTLE_MILLIS)
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>()).hasSize(1)
    }

    @Test
    fun `a failed chat-list load is not restarted by a Ready while teleX's own log out runs (AC-116, AC-113)`() {
        failFirstLoad()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        await().until { mainLoads(client) == 1 }
        Thread.ofVirtual().start { sessions.logOut(id, Duration.ofSeconds(LOG_OUT_SECONDS)) }
        await().until { TdlibRequest.LogOut in client.requests }

        reconnect(client)

        Thread.sleep(SETTLE_MILLIS)
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>()).hasSize(1)
    }

    /** The first main-list load is flooded (429, retry after 1 s), later ones complete; LogOut never finishes. */
    private fun floodFirstLoad() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        var mainLoads = 0
        tdlib.respond = { client, request ->
            val mainLoad = request is TdlibRequest.LoadChats && request.list == TdlibChatList.Main
            if (mainLoad && mainLoads++ == 0) {
                client.emit(TdlibUpdate.NewChat(chat(1)))
                TdlibResponse.Failure(429, "Too Many Requests: retry after 1")
            } else if (request is TdlibRequest.LoadChats) {
                TdlibResponse.Failure(404, "Not Found")
            } else if (request == TdlibRequest.LogOut) {
                client.emit(auth("authorizationStateLoggingOut"))
                TdlibResponse.Ok("ok")
            } else {
                null
            }
        }
    }

    private fun mainLoads(client: ScriptedTdlib.Client) =
        client.requests.filterIsInstance<TdlibRequest.LoadChats>().count { it.list == TdlibChatList.Main }

    @Test
    fun `a flooded chat-list load is retried after Telegram's retry-after, connection unchanged (AC-116, AC-121)`() {
        floodFirstLoad()
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()
        await().untilAsserted { assertThat(chats()).isNotEmpty() }
        Thread.sleep(SETTLE_MILLIS)
        assertThat(mainLoads(client)).isEqualTo(1)
        assertThat(chats().none { it.loadCompleted }).isTrue()

        await().atMost(Duration.ofMillis(BEFORE_BACKOFF_MILLIS)).untilAsserted {
            assertThat(chats().lastOrNull()?.loadCompleted).isTrue()
        }
        assertThat(mainLoads(client)).isEqualTo(2)
        assertThat(chats().last().loadedChatIds).containsExactly(1L)
        client.emit(TdlibUpdate.ChatTitle(1, "Renamed"))
        await().untilAsserted { assertThat(chats().last().upserted.map { it.title }).containsExactly("Renamed") }
    }

    @Test
    fun `a chat-list load retry that is still waiting when the session closes is not sent (AC-116)`() {
        floodFirstLoad()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        await().until { mainLoads(client) == 1 }

        sessions.close(id)

        Thread.sleep(RETRY_WAIT_MILLIS)
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>()).hasSize(1)
    }

    @Test
    fun `a chat-list load retry is not sent while teleX's own log out runs (AC-116, AC-113)`() {
        floodFirstLoad()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        await().until { mainLoads(client) == 1 }

        Thread.ofVirtual().start { sessions.logOut(id, Duration.ofSeconds(LOG_OUT_SECONDS)) }
        await().until { TdlibRequest.LogOut in client.requests }

        Thread.sleep(RETRY_WAIT_MILLIS)
        assertThat(client.requests.filterIsInstance<TdlibRequest.LoadChats>()).hasSize(1)
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
        const val RETRY_WAIT_MILLIS = 1_600L
        const val LOG_OUT_SECONDS = 4L

        /** Shorter than the first backoff (5 s), so only a connection update can have completed the load. */
        const val BEFORE_BACKOFF_MILLIS = 3_000L
    }
}
