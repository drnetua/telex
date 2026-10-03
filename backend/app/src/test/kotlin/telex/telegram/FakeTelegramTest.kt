package telex.telegram

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import telex.telegram.internal.fake.FakeTelegram
import telex.telegram.internal.files.SessionDirectories
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

class FakeTelegramTest {
    private val events = mutableListOf<Any>()
    private val root: Path = Files.createTempDirectory("telegram-fake")
    private val directories = SessionDirectories(root, Clock.systemUTC())
    private val fake = FakeTelegram(ApplicationEventPublisher { events += it }, configured = true, directories)
    private val id = fake.open(ByteArray(32))

    private fun states() = events.filterIsInstance<TelegramSessionStateChanged>()

    private fun chats() = events.filterIsInstance<TelegramChatsChanged>()

    private fun link(
        phone: String,
        session: TelegramSessionId = id,
    ): SignInOutcome {
        assertThat(fake.sendPhone(session, phone)).isInstanceOf(SignInOutcome.CodeSent::class.java)
        return fake.checkCode(session, FakeTelegram.CODE)
    }

    @Test
    fun `open creates the session directory and destroy removes it`() {
        assertThat(root.resolve(id.value.toString())).isDirectory()

        fake.destroy(id)

        assertThat(root.resolve(id.value.toString())).doesNotExist()
    }

    @Test
    fun `sweepOrphans keeps referenced sessions and removes the rest`() {
        val other = fake.open(ByteArray(32))

        fake.sweepOrphans(setOf(id))

        assertThat(root.resolve(id.value.toString())).exists()
        assertThat(root.resolve(other.value.toString())).doesNotExist()
    }

    @Test
    fun `configured reflects the api credentials`() {
        assertThat(fake.configured()).isTrue()
        assertThat(FakeTelegram(ApplicationEventPublisher { }, configured = false, directories).configured()).isFalse()
    }

    @Test
    fun `a plain number gets a code of the fixed length and authorizes`() {
        assertThat(fake.sendPhone(id, "9996600005")).isEqualTo(SignInOutcome.CodeSent(FakeTelegram.CODE.length))
        val outcome = fake.checkCode(id, FakeTelegram.CODE)

        assertThat(outcome).isInstanceOf(SignInOutcome.Authorized::class.java)
        val user = (outcome as SignInOutcome.Authorized).user
        assertThat(user.phoneCountryCode).isEqualTo("99")
        assertThat(user.phoneLastTwo).isEqualTo("05")
    }

    @Test
    fun `resend returns a code sent again`() {
        fake.sendPhone(id, "9996600005")
        assertThat(fake.resendCode(id)).isEqualTo(SignInOutcome.CodeSent(FakeTelegram.CODE.length))
    }

    @Test
    fun `a malformed number is invalid`() {
        assertThat(fake.sendPhone(id, "12")).isEqualTo(SignInOutcome.PhoneInvalid)
        assertThat(fake.sendPhone(id, "99966abcde")).isEqualTo(SignInOutcome.PhoneInvalid)
    }

    @Test
    fun `scripted refusals by the X digit`() {
        assertThat(fake.sendPhone(id, "9996633333")).isEqualTo(SignInOutcome.PhoneBanned)
        assertThat(fake.sendPhone(id, "9996644444")).isEqualTo(SignInOutcome.PhoneUnregistered)
        assertThat(fake.sendPhone(id, "9996655555"))
            .isEqualTo(SignInOutcome.WaitRequired(FakeTelegram.FLOOD_WAIT_SECONDS))
    }

    @Test
    fun `wrong and expired codes are told apart`() {
        fake.sendPhone(id, "9996600005")
        assertThat(fake.checkCode(id, "99999")).isEqualTo(SignInOutcome.CodeWrong)
        assertThat(fake.checkCode(id, FakeTelegram.EXPIRED_CODE)).isEqualTo(SignInOutcome.CodeExpired)
    }

    @Test
    fun `two-step account asks for a password with a hint and refuses a wrong one`() {
        assertThat(link("9996611111")).isEqualTo(SignInOutcome.PasswordNeeded(FakeTelegram.PASSWORD_HINT))
        assertThat(fake.checkPassword(id, "nope")).isEqualTo(SignInOutcome.PasswordWrong(FakeTelegram.PASSWORD_HINT))
        assertThat(fake.checkPassword(id, FakeTelegram.PASSWORD)).isInstanceOf(SignInOutcome.Authorized::class.java)
    }

    @Test
    fun `two-step account without a hint`() {
        assertThat(link("9996622222")).isEqualTo(SignInOutcome.PasswordNeeded(null))
        assertThat(fake.checkPassword(id, "nope")).isEqualTo(SignInOutcome.PasswordWrong(null))
    }

    @Test
    fun `authorization emits Ready then chat batches with total and completion`() {
        link("9996600120")

        assertThat(states().map { it.state }).containsExactly(SessionState.Ready)
        assertThat(chats().sumOf { it.upserted.size }).isEqualTo(120)
        assertThat(chats()).allSatisfy { assertThat(it.total).isEqualTo(120) }
        assertThat(chats().map { it.loadCompleted }.dropLast(1)).containsOnly(false)
        assertThat(chats().last().loadCompleted).isTrue()
        assertThat(chats().flatMap { it.upserted }.map { it.chatId }).doesNotHaveDuplicates()
    }

    @Test
    fun `an account with no chats completes at once`() {
        link("9996600000")

        assertThat(chats()).hasSize(1)
        assertThat(chats().single().upserted).isEmpty()
        assertThat(chats().single().loadCompleted).isTrue()
    }

    @Test
    fun `a dropped connection is Connecting never Closed, then Ready again`() {
        link("9996600005")
        fake.dropConnectivity(id)
        fake.restoreConnectivity(id)

        assertThat(states().map { it.state })
            .containsExactly(SessionState.Ready, SessionState.Connecting, SessionState.Ready)
        assertThat(states().map { it.sequence }).isSorted().doesNotHaveDuplicates()
    }

    @Test
    fun `termination emits Closed with a newer sequence`() {
        link("9996600005")
        fake.terminate(id)

        assertThat(states().last().state).isEqualTo(SessionState.Closed)
        assertThat(states().map { it.sequence }).isSorted().doesNotHaveDuplicates()
    }

    @Test
    fun `the terminate-after-link number closes the session right after authorization`() {
        link("9996677705")

        assertThat(states().map { it.state }).containsExactly(SessionState.Ready, SessionState.Closed)
        assertThat(states().map { it.sequence }).isSorted().doesNotHaveDuplicates()
    }

    @Test
    fun `sequences are per session`() {
        link("9996600005")
        val other = fake.open(ByteArray(32))
        link("9996600006", other)

        val first = { session: TelegramSessionId -> states().first { it.sessionId == session }.sequence }
        assertThat(first(other)).isEqualTo(first(id))
    }

    @Test
    fun `logOut confirms when reachable and is false after the timeout when not`() {
        link("9996600005")
        assertThat(fake.logOut(id, Duration.ofMillis(10))).isTrue()

        val second = fake.open(ByteArray(32))
        link("9996600006", second)
        fake.dropConnectivity(second)
        assertThat(fake.logOut(second, Duration.ofMillis(10))).isFalse()
    }

    @Test
    fun `an unreachable Telegram does not answer sign-in steps`() {
        fake.dropConnectivity(id)
        assertThatThrownBy { fake.sendPhone(id, "9996600005") }.isInstanceOf(TelegramUnavailable::class.java)
    }

    @Test
    fun `destroyed sessions are forgotten`() {
        link("9996600005")
        fake.close(id)
        fake.reopen(id, ByteArray(32))
        fake.destroy(id)

        assertThatThrownBy { fake.sendPhone(id, "9996600005") }.isInstanceOf(IllegalStateException::class.java)
    }
}
