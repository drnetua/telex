package telex.messaging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.shared.Uuid7
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.io.path.listDirectoryEntries

private val SESSIONS_DIR: Path = Files.createTempDirectory("telex-linking-sessions")
private const val START = "2026-10-02T10:00:00Z"

private fun sessionDirectories() = SESSIONS_DIR.listDirectoryEntries()

private fun configure(
    registry: DynamicPropertyRegistry,
    credentials: Boolean,
) {
    registry.add("telex.telegram.adapter") { "fake" }
    registry.add("telex.telegram.sessions-dir") { SESSIONS_DIR.toString() }
    registry.add("telex.telegram.api-id") { if (credentials) "12345" else "" }
    registry.add("telex.telegram.api-hash") { if (credentials) "abcdef" else "" }
    registry.add("telex.telegram.max-accounts-per-owner") { "2" }
    registry.add("telex.master-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
}

/** AC-109 (one attempt per Owner, resume, cancel, 15 min), AC-110 (session ends mid-wizard), AC-114 (add another). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class LinkingAttemptIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private var owner = OwnerId(UUID.randomUUID())
    private var session = SignInSessionId(UUID.randomUUID())

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse(START))
        owner = newOwner()
        session = signIn(owner)
    }

    @AfterEach
    fun cleanUp() = linking.cancel(owner)

    private fun newOwner(): OwnerId {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun signIn(owner: OwnerId) = signInSessions.start(owner, null, null, null, false).sessionId

    private fun account(
        owner: OwnerId,
        state: String = "connected",
    ): UUID {
        val id = Uuid7.next()
        val sessionId = if (state == "session_lost") null else UUID.randomUUID()
        jdbc.update(
            "INSERT INTO linked_account (id, owner_id, telegram_user_id, telegram_session_id, tdlib_key_sealed, " +
                "display_name, phone_country_code, phone_last_digits, state, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 'Anna', '380', '42', ?, now())",
            id,
            owner.value,
            Math.abs(UUID.randomUUID().mostSignificantBits),
            sessionId,
            if (state == "session_lost") null else ByteArray(60),
            state,
        )
        return id
    }

    private fun start(
        by: SignInSessionId = session,
        target: UUID? = null,
        origin: LinkingOrigin = LinkingOrigin.INBOX,
    ) = linking.start(owner, by, origin, target?.let { LinkedAccountId(it) })

    @Test
    fun `starting opens a new attempt at the phone step with a Telegram session`() {
        val before = sessionDirectories().size

        val started = start()

        assertThat(started.resumed).isFalse()
        assertThat(started.attempt.step).isEqualTo(LinkingStep.PHONE)
        assertThat(started.attempt.origin).isEqualTo(LinkingOrigin.INBOX)
        assertThat(sessionDirectories()).hasSize(before + 1)
    }

    @Test
    fun `starting again from another device resumes the same attempt and ignores the body`() {
        val first = start()
        val other = signIn(owner)

        val second = start(by = other, origin = LinkingOrigin.ACCOUNTS)

        assertThat(second.resumed).isTrue()
        assertThat(second.attempt).isEqualTo(first.attempt)
        assertThat(linking.get(owner)).isEqualTo(first.attempt)
    }

    @Test
    fun `cancel discards the attempt and its Telegram session, then start begins at the phone step`() {
        val before = sessionDirectories().size
        start()

        linking.cancel(owner)

        assertThat(sessionDirectories()).hasSize(before)
        assertThatThrownBy { linking.get(owner) }.isInstanceOf(LinkingAttemptNotFound::class.java)
        assertThat(start().resumed).isFalse()
        linking.cancel(owner)
    }

    @Test
    fun `cancel with nothing open does nothing`() {
        linking.cancel(owner)
        linking.cancel(owner)
    }

    @Test
    fun `fifteen minutes without a step discards the attempt in the sweep`() {
        val before = sessionDirectories().size
        start()

        clock.advance(Duration.ofMinutes(15).minusSeconds(1))
        linking.sweep()
        assertThat(linking.get(owner).step).isEqualTo(LinkingStep.PHONE)

        clock.advance(Duration.ofSeconds(1))
        linking.sweep()
        assertThat(sessionDirectories()).hasSize(before)
        assertThatThrownBy { linking.get(owner) }.isInstanceOf(LinkingAttemptNotFound::class.java)
        assertThat(start().resumed).isFalse()
    }

    @Test
    fun `a Sign-in Session that ends mid-wizard discards the attempt on the next read and in the sweep`() {
        val before = sessionDirectories().size
        val kept = account(owner)
        start()

        signInSessions.endMine(owner, session)

        assertThatThrownBy { linking.get(owner) }.isInstanceOf(LinkingAttemptNotFound::class.java)
        assertThat(sessionDirectories()).hasSize(before)
        assertThat(jdbc.queryForObject("SELECT state FROM linked_account WHERE id = ?", String::class.java, kept))
            .isEqualTo("connected")

        val next = signIn(owner)
        start(by = next)
        signInSessions.endMine(owner, next)
        linking.sweep()
        assertThat(sessionDirectories()).hasSize(before)
        assertThat(start(by = signIn(owner)).resumed).isFalse()
    }

    @Test
    fun `an ended session also discards the attempt on start, which then begins fresh`() {
        start()
        signInSessions.endMine(owner, session)

        val started = start(by = signIn(owner))

        assertThat(started.resumed).isFalse()
        assertThat(started.attempt.step).isEqualTo(LinkingStep.PHONE)
    }

    @Test
    fun `an Owner below the limit can add another account`() {
        account(owner)

        assertThat(start(origin = LinkingOrigin.ACCOUNTS).attempt.origin).isEqualTo(LinkingOrigin.ACCOUNTS)
    }

    @Test
    fun `an Owner at the limit is refused with the limit and nothing opens`() {
        account(owner)
        account(owner, "session_lost")
        val before = sessionDirectories().size

        assertThatThrownBy { start() }
            .isInstanceOfSatisfying(LinkedAccountLimitReached::class.java) {
                assertThat(it.body.properties).containsEntry("limit", 2)
                assertThat(it.body.properties).containsEntry("code", "linked-account-limit-reached")
            }
        assertThat(sessionDirectories()).hasSize(before)
    }

    @Test
    fun `sign in again targets only the Owner's Session lost account and skips the limit check`() {
        account(owner)
        val lost = account(owner, "session_lost")

        val started = start(target = lost)

        assertThat(started.attempt.targetLinkedAccountId).isEqualTo(LinkedAccountId(lost))
        linking.cancel(owner)
        assertThatThrownBy { start(target = account(owner, "connected")) }
            .isInstanceOf(TelegramAccountAlreadyLinked::class.java)
        assertThatThrownBy { start(target = account(newOwner(), "session_lost")) }
            .isInstanceOf(LinkedAccountNotFound::class.java)
        assertThatThrownBy { start(target = UUID.randomUUID()) }.isInstanceOf(LinkedAccountNotFound::class.java)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = configure(registry, credentials = true)
    }
}

/** AC-119: an installation without Telegram app credentials refuses to start the wizard. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class LinkingNotSetUpIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `starting without Telegram credentials is refused as not set up and opens nothing`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val session = signInSessions.start(owner, null, null, null, false).sessionId
        val before = sessionDirectories().size

        assertThatThrownBy { linking.start(owner, session, LinkingOrigin.INBOX, null) }
            .isInstanceOfSatisfying(TelegramLinkingNotSetUp::class.java) {
                assertThat(it.body.properties).containsEntry("code", "telegram-linking-not-set-up")
                assertThat(it.statusCode.value()).isEqualTo(503)
            }
        assertThat(sessionDirectories()).hasSize(before)
        assertThatThrownBy { linking.get(owner) }.isInstanceOf(LinkingAttemptNotFound::class.java)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = configure(registry, credentials = false)
    }
}

/** AC-119: credentials without a master key on a fresh installation still start the app and report "not set up". */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class LinkingNoMasterKeyIT {
    @Autowired lateinit var linking: Linking

    @Autowired lateinit var signInSessions: SignInSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `starting without a master key is refused as not set up`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val session = signInSessions.start(owner, null, null, null, false).sessionId

        assertThatThrownBy { linking.start(owner, session, LinkingOrigin.INBOX, null) }
            .isInstanceOf(TelegramLinkingNotSetUp::class.java)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            configure(registry, credentials = true)
            registry.add("telex.master-key") { "" }
        }
    }
}
