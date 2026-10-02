package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import telex.TestcontainersConfiguration
import telex.identity.internal.owner.Owners
import telex.identity.internal.secret.Secrets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class MutableClock(
    private var now: Instant,
) : Clock() {
    fun set(instant: Instant) {
        now = instant
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

@TestConfiguration(proxyBeanMethods = false)
class FixedClockConfiguration {
    @Bean
    @Primary
    fun testClock(): MutableClock = MutableClock(Instant.parse("2026-10-02T10:00:00Z"))
}

/** AC-96 (30-day idle / 90-day cap, background ignored) and AC-104 (new sign-in ends the held one). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
@RecordApplicationEvents
class SignInSessionsIT {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var events: ApplicationEvents

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T10:00:00Z"))
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun anOwner(email: String = "anton@mail.com"): OwnerId =
        owners.findOrCreate(email, email, clock.instant()).first

    private fun start(
        owner: OwnerId,
        held: String? = null,
        timeZone: String? = "Europe/Kyiv",
        created: Boolean = false,
    ) = sessions.start(owner, held, "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0", timeZone, created)

    private fun lastActivity(id: SignInSessionId): Instant =
        jdbc
            .queryForObject(
                "SELECT last_activity_at FROM sign_in_session WHERE id = ?",
                java.sql.Timestamp::class.java,
                id.value,
            )!!
            .toInstant()

    private fun endedAt(id: SignInSessionId): Instant? =
        jdbc
            .queryForObject(
                "SELECT ended_at FROM sign_in_session WHERE id = ?",
                java.sql.Timestamp::class.java,
                id.value,
            )?.toInstant()

    @Test
    fun `start stores only the key hash and publishes the event without secrets`() {
        val owner = anOwner()
        val s = start(owner, created = true)
        val stored =
            jdbc.queryForObject(
                "SELECT key_hash FROM sign_in_session WHERE id = ?",
                ByteArray::class.java,
                s.sessionId.value,
            )
        assertThat(stored).isEqualTo(Secrets.sha256(s.key))
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sign_in_session WHERE encode(key_hash,'escape') = ?",
                Int::class.java,
                s.key,
            ),
        ).isZero()
        val published = events.stream(SignInSessionStarted::class.java).toList()
        assertThat(published).containsExactly(SignInSessionStarted(owner, s.sessionId, true))
        assertThat(sessions.resolve(s.key, background = false)).isEqualTo(SessionResolution.Live(owner, s.sessionId))
    }

    @Test
    fun `AC-104 starting while holding a session ends the held one even for another owner`() {
        val first = start(anOwner("a@mail.com"))
        val other = anOwner("b@mail.com")
        val second = start(other, held = first.key)
        assertThat(endedAt(first.sessionId)).isEqualTo(clock.instant())
        assertThat(sessions.resolve(first.key, false)).isEqualTo(SessionResolution.Ended)
        assertThat(sessions.resolve(second.key, false)).isEqualTo(SessionResolution.Live(other, second.sessionId))
    }

    @Test
    fun `an unknown or ended held key is ignored`() {
        val owner = anOwner()
        assertThat(start(owner, held = "nope").key).isNotBlank()
        val a = start(owner)
        sessions.endByKey(a.key)
        assertThat(start(owner, held = a.key).key).isNotBlank()
    }

    @Test
    fun `unknown time zone is stored as UTC`() {
        val s = start(anOwner(), timeZone = "Mars/Base")
        assertThat(
            jdbc.queryForObject(
                "SELECT time_zone FROM sign_in_session WHERE id = ?",
                String::class.java,
                s.sessionId.value,
            ),
        ).isEqualTo("UTC")
        val m = start(anOwner(), timeZone = null)
        assertThat(
            jdbc.queryForObject(
                "SELECT time_zone FROM sign_in_session WHERE id = ?",
                String::class.java,
                m.sessionId.value,
            ),
        ).isEqualTo("UTC")
    }

    @Test
    fun `AC-96 background-only requests for 31 days end the session and mark the row`() {
        val s = start(anOwner())
        clock.advance(Duration.ofDays(31))
        assertThat(sessions.resolve(s.key, background = true)).isEqualTo(SessionResolution.Ended)
        assertThat(endedAt(s.sessionId)).isEqualTo(clock.instant())
    }

    @Test
    fun `AC-96 a user request on day 29 keeps the session live on day 30`() {
        val owner = anOwner()
        val s = start(owner)
        clock.advance(Duration.ofDays(29))
        assertThat(sessions.resolve(s.key, background = false)).isEqualTo(SessionResolution.Live(owner, s.sessionId))
        clock.advance(Duration.ofDays(1))
        assertThat(sessions.resolve(s.key, background = false)).isEqualTo(SessionResolution.Live(owner, s.sessionId))
    }

    @Test
    fun `AC-96 idle for 30 days ends the session`() {
        val s = start(anOwner())
        clock.advance(Duration.ofDays(30).plusSeconds(1))
        assertThat(sessions.resolve(s.key, background = false)).isEqualTo(SessionResolution.Ended)
    }

    @Test
    fun `AC-96 daily activity still ends at 90 days from start`() {
        val owner = anOwner()
        val s = start(owner)
        repeat(89) {
            clock.advance(Duration.ofDays(1))
            assertThat(sessions.resolve(s.key, false)).isEqualTo(SessionResolution.Live(owner, s.sessionId))
        }
        clock.advance(Duration.ofDays(1).plusSeconds(1))
        assertThat(sessions.resolve(s.key, false)).isEqualTo(SessionResolution.Ended)
        assertThat(endedAt(s.sessionId)).isNotNull()
    }

    @Test
    fun `activity is bumped at most once a minute and never by background requests`() {
        val s = start(anOwner())
        val t0 = clock.instant()
        clock.advance(Duration.ofSeconds(30))
        sessions.resolve(s.key, false)
        assertThat(lastActivity(s.sessionId)).isEqualTo(t0)
        clock.advance(Duration.ofSeconds(40))
        sessions.resolve(s.key, true)
        assertThat(lastActivity(s.sessionId)).isEqualTo(t0)
        sessions.resolve(s.key, false)
        val bumped = lastActivity(s.sessionId)
        assertThat(bumped).isEqualTo(clock.instant())
        clock.advance(Duration.ofSeconds(20))
        sessions.resolve(s.key, false)
        assertThat(lastActivity(s.sessionId)).isEqualTo(bumped)
    }

    @Test
    fun `unknown key resolves to Unknown and endByKey is idempotent`() {
        assertThat(sessions.resolve("missing", false)).isEqualTo(SessionResolution.Unknown)
        sessions.endByKey(null)
        sessions.endByKey("missing")
        val s = start(anOwner())
        sessions.endByKey(s.key)
        val ended = endedAt(s.sessionId)
        clock.advance(Duration.ofMinutes(5))
        sessions.endByKey(s.key)
        assertThat(endedAt(s.sessionId)).isEqualTo(ended)
        assertThat(sessions.resolve(s.key, false)).isEqualTo(SessionResolution.Ended)
    }

    @Test
    fun `concurrent first sign-ins for one canonical address create one owner`() {
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        val tasks =
            List(2) {
                Callable {
                    gate.await()
                    owners.findOrCreate("Anton+x@Mail.com", "anton@mail.com", clock.instant())
                }
            }
        val futures = tasks.map(pool::submit)
        gate.countDown()
        val results = futures.map { it.get() }
        pool.shutdown()
        assertThat(results.map { it.first }.distinct()).hasSize(1)
        assertThat(results.count { it.second }).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM owner", Int::class.java)).isEqualTo(1)
    }
}
