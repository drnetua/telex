package telex.telegram.internal.tdlight

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import telex.telegram.SessionState
import telex.telegram.SignInOutcome
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.TelegramUnavailable
import telex.telegram.internal.files.SessionDirectories
import telex.telegram.tdlib.TdlibRequest
import telex.telegram.tdlib.TdlibResponse
import telex.telegram.tdlib.TdlibUpdate
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

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

    private fun auth(
        state: String,
        codeLength: Int? = null,
        hint: String? = null,
    ) = TdlibUpdate.AuthorizationState(state, codeLength, hint)

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

    private fun codeStep(
        client: ScriptedTdlib.Client,
        code: String,
    ): TdlibResponse =
        when (code) {
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

            "44444" -> {
                client.emit(auth("authorizationStateWaitRegistration"))
                TdlibResponse.Ok("ok")
            }

            else -> {
                client.emit(auth("authorizationStateReady"))
                TdlibResponse.Ok("ok")
            }
        }

    private fun signedIn(sessions: TdlightTelegramSessions): TelegramSessionId {
        val id = sessions.open(key)
        sessions.sendPhone(id, "380501234564")
        sessions.checkCode(id, "33333")
        sessions.checkPassword(id, "secret")
        return id
    }

    private fun scriptSignIn() {
        tdlib.respond = { client, request ->
            when (request) {
                is TdlibRequest.SetPhoneNumber -> {
                    phoneStep(client, request)
                }

                is TdlibRequest.CheckCode -> {
                    codeStep(client, request.code)
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
    fun `WaitRegistration after the code maps to PhoneUnregistered and closes the client`() {
        scriptSignIn()
        val sessions = create()
        val id = sessions.open(key)
        sessions.sendPhone(id, "380501234564")

        assertThat(sessions.checkCode(id, "44444")).isEqualTo(SignInOutcome.PhoneUnregistered)
        assertThat(tdlib.clients.single().closeCalled).isTrue()
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
    fun `a resend Telegram refuses for the phone maps to the phone step's outcome (AC-107)`() {
        scriptSignIn()
        val sessions = create()

        fun resendRefused(failure: TdlibResponse.Failure): SignInOutcome {
            val id = sessions.open(key)
            sessions.sendPhone(id, "380501234564")
            val signIn = tdlib.respond
            tdlib.respond =
                { client, request -> if (request == TdlibRequest.ResendCode) failure else signIn(client, request) }
            return sessions.resendCode(id).also { tdlib.respond = signIn }
        }

        assertThat(resendRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_INVALID")))
            .isEqualTo(SignInOutcome.PhoneInvalid)
        assertThat(resendRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_BANNED")))
            .isEqualTo(SignInOutcome.PhoneBanned)
        assertThat(resendRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_UNOCCUPIED")))
            .isEqualTo(SignInOutcome.PhoneUnregistered)
        assertThat(resendRefused(TdlibResponse.Failure(429, "Too Many Requests: retry after 30")))
            .isEqualTo(SignInOutcome.WaitRequired(30))
        assertThatThrownBy { resendRefused(TdlibResponse.Failure(500, "INTERNAL")) }
            .isInstanceOf(TelegramUnavailable::class.java)
    }

    @Test
    fun `a code check Telegram refuses for the phone maps to the phone step's outcome (AC-107)`() {
        scriptSignIn()
        val sessions = create()

        fun codeRefused(failure: TdlibResponse.Failure): SignInOutcome {
            val id = sessions.open(key)
            sessions.sendPhone(id, "380501234564")
            val signIn = tdlib.respond
            tdlib.respond =
                { client, request -> if (request is TdlibRequest.CheckCode) failure else signIn(client, request) }
            return sessions.checkCode(id, "12345").also { tdlib.respond = signIn }
        }

        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_INVALID")))
            .isEqualTo(SignInOutcome.PhoneInvalid)
        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_BANNED")))
            .isEqualTo(SignInOutcome.PhoneBanned)
        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_NUMBER_UNOCCUPIED")))
            .isEqualTo(SignInOutcome.PhoneUnregistered)
        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_CODE_INVALID"))).isEqualTo(SignInOutcome.CodeWrong)
        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_CODE_EMPTY"))).isEqualTo(SignInOutcome.CodeWrong)
        assertThat(codeRefused(TdlibResponse.Failure(400, "PHONE_CODE_EXPIRED"))).isEqualTo(SignInOutcome.CodeExpired)
        assertThat(codeRefused(TdlibResponse.Failure(429, "Too Many Requests: retry after 30")))
            .isEqualTo(SignInOutcome.WaitRequired(30))
        assertThatThrownBy { codeRefused(TdlibResponse.Failure(500, "INTERNAL")) }
            .isInstanceOf(TelegramUnavailable::class.java)
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
    fun `a reopen that races one finishing opens one client and keeps the first session (AC-111)`() {
        val firstInsideOpen = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        tdlib.onOpen = {
            if (tdlib.clients.size == 1) {
                firstInsideOpen.countDown()
                releaseFirst.await()
            }
            it.emit(auth("authorizationStateReady"))
        }
        answerOnlyChatLoads()
        val sessions = create()
        val id = TelegramSessionId(telex.shared.Uuid7.next())
        val virtualThreads = Executors.newVirtualThreadPerTaskExecutor()
        // the second reopen parks when it claims the in-flight marker until the first reopen has fully finished
        val secondClaiming = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val second = AtomicReference<Thread>()
        ReflectionTestUtils.setField(
            sessions,
            "opening",
            object : ConcurrentHashMap<TelegramSessionId, CompletableFuture<Unit>>() {
                override fun putIfAbsent(
                    key: TelegramSessionId,
                    value: CompletableFuture<Unit>,
                ): CompletableFuture<Unit>? {
                    if (Thread.currentThread() == second.get()) {
                        secondClaiming.countDown()
                        firstDone.await()
                    }
                    return super.putIfAbsent(key, value)
                }
            },
        )
        val first = CompletableFuture.runAsync({ sessions.reopen(id, key) }, virtualThreads)
        firstInsideOpen.await()
        val secondDone = CompletableFuture<Unit>()
        second.set(
            Thread.ofVirtual().unstarted {
                runCatching { sessions.reopen(id, key) }
                    .fold({ secondDone.complete(Unit) }, { secondDone.completeExceptionally(it) })
            },
        )
        second.get().start()

        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            secondClaiming.await()
            releaseFirst.countDown()
            first.get()
            firstDone.countDown()
            secondDone.get()
        }

        assertThat(tdlib.clients).hasSize(1)
        assertThat(tdlib.clients.single().closeCalled).isFalse()
        await().untilAsserted { assertThat(states().map { it.state }).containsExactly(SessionState.Ready) }
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
    fun `Updating is connected and catching up, so it is Ready and never Connecting (AC-122)`() {
        tdlib.onOpen = { it.emit(auth("authorizationStateReady")) }
        answerOnlyChatLoads()
        create().reopen(TelegramSessionId(telex.shared.Uuid7.next()), key)
        val client = tdlib.clients.single()

        client.emit(TdlibUpdate.ConnectionState("connectionStateUpdating"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateConnectingToProxy"))
        client.emit(TdlibUpdate.ConnectionState("connectionStateUpdating"))

        await().untilAsserted {
            assertThat(states().map { it.state })
                .containsExactly(SessionState.Ready, SessionState.Connecting, SessionState.Ready)
        }
        Thread.sleep(SETTLE_MILLIS)
        assertThat(states().map { it.state })
            .containsExactly(SessionState.Ready, SessionState.Connecting, SessionState.Ready)
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
        val id = signedIn(sessions)
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

        scriptSignIn()
        val stuck = signedIn(sessions)
        tdlib.respond = { _, _ -> null }
        assertThat(sessions.logOut(stuck, Duration.ofMillis(200))).isFalse()
    }

    @Test
    fun `a session is authorized once Ready is reached, also when the step that saw it timed out (AC-109, AC-04)`() {
        scriptSignIn()
        val sessions = create(Duration.ofMillis(200))
        val id = sessions.open(key)
        assertThat(sessions.authorized(id)).isFalse()
        tdlib.respond = { client, request ->
            if (request is TdlibRequest.CheckCode) client.emit(auth("authorizationStateReady"))
            null
        }
        assertThatThrownBy { sessions.checkCode(id, "12345") }.isInstanceOf(TelegramUnavailable::class.java)
        await().untilAsserted { assertThat(sessions.authorized(id)).isTrue() }

        sessions.close(id)
        assertThat(sessions.authorized(id)).isFalse()
    }

    @Test
    fun `a client that fails to open leaves nothing behind and shutdown does not hang`() {
        tdlib.openFailure = IllegalStateException("tdlib native load failed")
        val sessions = create()

        assertThatThrownBy { sessions.open(key) }.isInstanceOf(IllegalStateException::class.java)

        assertTimeoutPreemptively(Duration.ofSeconds(3)) { sessions.shutdown() }
        assertThat(root.toFile().listFiles().orEmpty()).isEmpty()
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
