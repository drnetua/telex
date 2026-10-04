package telex.telegram.internal.tdlight

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import telex.telegram.SessionState
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.internal.files.SessionDirectories
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/** The tdlight adapter's `logOut` (AC-111, AC-113, AC-122): when it signs out, and what only TDLib can confirm. */
class TdlightLogOutTest {
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

    private fun auth(state: String) = TdlibUpdate.AuthorizationState(state, null, null)

    private fun answerOnlyChatLoads() {
        tdlib.respond =
            { _, request -> if (request is TdlibRequest.LoadChats) TdlibResponse.Failure(404, "") else null }
    }

    /** Runs [call] on its own virtual thread, so a test can act while the call is parked. */
    private fun <T> inBackground(call: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        Thread.ofVirtual().start {
            try {
                result.complete(call())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
            }
        }
        return result
    }

    /** A reopened, Ready session whose LogOut TDLib answers with LoggingOut, then [finish] (Closed when true). */
    private fun readyForLogOut(finish: Boolean): Pair<TdlightTelegramSessions, TelegramSessionId> {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        tdlib.respond = { client, request ->
            when (request) {
                TdlibRequest.LogOut -> {
                    client.emit(auth("authorizationStateLoggingOut"))
                    if (finish) {
                        client.emit(auth("authorizationStateClosing"))
                        client.emit(TdlibUpdate.Closed)
                    }
                    TdlibResponse.Ok("ok")
                }

                is TdlibRequest.LoadChats -> {
                    TdlibResponse.Failure(404, "")
                }

                else -> {
                    null
                }
            }
        }
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        await().untilAsserted { assertThat(states().map { it.state }).containsExactly(SessionState.Ready) }
        return sessions to id
    }

    @Test
    fun `after a logOut TDLib did not finish in time, a later Closed is still announced (AC-122, AC-117)`() {
        val (sessions, id) = readyForLogOut(finish = false)
        val client = tdlib.clients.single()

        assertThat(sessions.logOut(id, Duration.ofMillis(200))).isFalse()
        client.emit(TdlibUpdate.Closed)

        await().atMost(Duration.ofSeconds(2)).untilAsserted {
            assertThat(states().map { it.state }).containsExactly(SessionState.Ready, SessionState.Closed)
        }
    }

    @Test
    fun `teleX's own logOut publishes no Closed for TDLib's LoggingOut and Closed (AC-113, AC-122)`() {
        val (sessions, id) = readyForLogOut(finish = true)

        assertThat(sessions.logOut(id, Duration.ofSeconds(2))).isTrue()

        await()
            .during(Duration.ofMillis(300))
            .atMost(Duration.ofSeconds(2))
            .untilAsserted { assertThat(states().map { it.state }).containsExactly(SessionState.Ready) }
    }

    @Test
    fun `a logOut that teleX's own close ends before TDLib finishes it is not confirmed (AC-113, AC-111)`() {
        val (sessions, id) = readyForLogOut(finish = false)
        val client = tdlib.clients.single()
        val confirmed = inBackground { sessions.logOut(id, Duration.ofSeconds(5)) }
        await().untilAsserted { assertThat(client.requests).contains(TdlibRequest.LogOut) }

        sessions.close(id)

        assertTimeoutPreemptively(Duration.ofSeconds(2)) { assertThat(confirmed.get()).isFalse() }
        assertThat(states().map { it.state }).containsExactly(SessionState.Ready)
    }

    @Test
    fun `logOut of a session that is registered but already closed is not confirmed (AC-113)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        client.emit(TdlibUpdate.Closed)

        assertThat(sessions.logOut(id, Duration.ofMillis(200))).isFalse()
        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a session that has not signed in yet is not confirmed (AC-113)`() {
        val sessions = create()
        val id = sessions.open(key)

        assertThat(sessions.logOut(id, Duration.ofMillis(200))).isFalse()
        assertThat(tdlib.clients.single().requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a fresh session returns false at once even when Ready arrives, and sends no LogOut (AC-113)`() {
        val sessions = create()
        val id = sessions.open(key)
        val client = tdlib.clients.single()

        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            assertThat(sessions.logOut(id, Duration.ofSeconds(5))).isFalse()
        }
        client.emit(auth("authorizationStateReady"))

        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a reopened session that ends while waiting returns false promptly (AC-113)`() {
        tdlib.onOpen = {}
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        val confirmed = inBackground { sessions.logOut(id, Duration.ofSeconds(5)) }
        await().until { sessions.awaitingAuthorization(id) }

        client.emit(auth("authorizationStateWaitPhoneNumber"))

        assertTimeoutPreemptively(Duration.ofSeconds(2)) { assertThat(confirmed.get()).isFalse() }
        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a session TDLib is already logging out remotely is not confirmed (AC-113)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        client.emit(auth("authorizationStateLoggingOut"))

        assertThat(sessions.logOut(id, Duration.ofMillis(200))).isFalse()
        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a reopened session that is not Ready yet waits for Ready, then signs it out (AC-113, AC-111)`() {
        tdlib.onOpen = {}
        tdlib.respond = { client, request ->
            when (request) {
                TdlibRequest.LogOut -> {
                    client.emit(auth("authorizationStateLoggingOut"))
                    client.emit(TdlibUpdate.Closed)
                    TdlibResponse.Ok("ok")
                }

                is TdlibRequest.LoadChats -> {
                    TdlibResponse.Failure(404, "")
                }

                else -> {
                    null
                }
            }
        }
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        val confirmed = inBackground { sessions.logOut(id, Duration.ofSeconds(4)) }
        await().until { sessions.awaitingAuthorization(id) }

        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
        client.emit(auth("authorizationStateReady"))

        assertThat(confirmed.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue()
        assertThat(client.requests).contains(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a reopened session that never reaches Ready is not confirmed and sends nothing (AC-113)`() {
        tdlib.onOpen = {}
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)

        assertThat(sessions.logOut(id, Duration.ofMillis(200))).isFalse()
        assertThat(tdlib.clients.single().requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a reopened session that Telegram ended before Ready is not confirmed (AC-113)`() {
        tdlib.onOpen = {}
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        sessions.reopen(id, key)
        val client = tdlib.clients.single()
        client.emit(auth("authorizationStateWaitPhoneNumber"))

        assertThat(sessions.logOut(id, Duration.ofSeconds(2))).isFalse()
        assertThat(client.requests).doesNotContain(TdlibRequest.LogOut)
    }

    @Test
    fun `logOut of a session that is not open is not confirmed (AC-113)`() {
        val sessions = create()

        assertThat(sessions.logOut(TelegramSessionId(telex.shared.Uuid7.next()), Duration.ofMillis(50))).isFalse()
    }
}
