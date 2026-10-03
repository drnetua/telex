package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.identity.internal.key.MasterKey
import telex.identity.internal.key.MasterKeyCheck
import telex.identity.internal.key.OwnerKeyRows
import telex.identity.internal.owner.Owners
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** AC-119 (no master key on a fresh installation = linking not set up) and AC-110 (session liveness). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class OwnerKeysIT {
    @Autowired lateinit var rows: OwnerKeyRows

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var unconfigured: OwnerKeys

    private val noArgs = DefaultApplicationArguments()
    private val keyA = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val keyB = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T10:00:00Z"))
        jdbc.execute("DELETE FROM owner_key")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    @AfterEach
    fun cleanUp() {
        jdbc.execute("DELETE FROM owner_key")
    }

    private fun keys(master: String?) = OwnerKeys(MasterKey(master.orEmpty()), rows, clock)

    private fun check(
        master: String?,
        reset: Boolean = false,
        keys: OwnerKeys = keys(master),
    ) = MasterKeyCheck(MasterKey(master.orEmpty()), reset, rows, keys)

    private fun anOwner(email: String = "anton@mail.com"): OwnerId =
        owners.findOrCreate(email, email, clock.instant()).first

    private fun count() = jdbc.queryForObject("SELECT count(*) FROM owner_key", Int::class.java)!!

    @Test
    fun `a fresh installation without a master key starts and is not ready`() {
        assertThat(unconfigured.ready()).isFalse()
        check(null).run(noArgs)
        assertThat(keys(keyA).ready()).isTrue()
    }

    @Test
    fun `seal lazily creates one owner key and open round-trips with the aad`() {
        val owner = anOwner()
        val k = keys(keyA)
        assertThat(count()).isZero()
        val sealed = k.seal(owner, "tdlib-key".toByteArray(), "acc-1".toByteArray())
        assertThat(count()).isEqualTo(1)
        assertThat(String(k.open(owner, sealed, "acc-1".toByteArray()))).isEqualTo("tdlib-key")
        k.seal(owner, "x".toByteArray(), "acc-2".toByteArray())
        assertThat(count()).isEqualTo(1)
        assertThatThrownBy { k.open(owner, sealed, "acc-2".toByteArray()) }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `the stored key is sealed at 60 bytes and unreadable under another master key`() {
        val owner = anOwner()
        keys(keyA).seal(owner, "p".toByteArray(), "a".toByteArray())
        val len = jdbc.queryForObject("SELECT octet_length(sealed_key) FROM owner_key", Int::class.java)
        assertThat(len).isEqualTo(60)
        assertThatThrownBy { keys(keyB).seal(owner, "p".toByteArray(), "a".toByteArray()) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `startup passes with the right key and refuses a missing or wrong key naming the setting`() {
        keys(keyA).seal(anOwner(), "p".toByteArray(), "a".toByteArray())
        check(keyA).run(noArgs)
        assertThatThrownBy { check(null).run(noArgs) }.hasMessageContaining("TELEX_MASTER_KEY")
        assertThatThrownBy { check(keyB).run(noArgs) }.hasMessageContaining("TELEX_MASTER_KEY")
    }

    @Test
    fun `reset deletes every owner key and is reported, so the new key starts the check afresh`() {
        keys(keyA).seal(anOwner(), "p".toByteArray(), "a".toByteArray())
        val k = keys(keyB)
        assertThat(k.resetPerformedAtStartup).isFalse()
        check(keyB, reset = true, keys = k).run(noArgs)
        assertThat(count()).isZero()
        assertThat(k.resetPerformedAtStartup).isTrue()
        k.seal(anOwner("b@mail.com"), "p".toByteArray(), "a".toByteArray())
        check(keyB).run(noArgs)
    }

    @Test
    fun `isLive is true for a live session and false for ended, idle and unknown ones without bumping activity`() {
        val owner = anOwner()
        val s = sessions.start(owner, null, "Firefox/130.0", "UTC", false)
        val before = lastActivity(s.sessionId)
        clock.advance(Duration.ofMinutes(10))
        assertThat(sessions.isLive(s.sessionId)).isTrue()
        assertThat(lastActivity(s.sessionId)).isEqualTo(before)
        sessions.endMine(owner, s.sessionId)
        assertThat(sessions.isLive(s.sessionId)).isFalse()
        assertThat(sessions.isLive(SignInSessionId(UUID.randomUUID()))).isFalse()
        val idle = sessions.start(owner, null, "Firefox/130.0", "UTC", false)
        clock.advance(Duration.ofDays(31))
        assertThat(sessions.isLive(idle.sessionId)).isFalse()
    }

    private fun lastActivity(id: SignInSessionId): Instant =
        jdbc
            .queryForObject(
                "SELECT last_activity_at FROM sign_in_session WHERE id = ?",
                Timestamp::class.java,
                id.value,
            )!!
            .toInstant()
}
