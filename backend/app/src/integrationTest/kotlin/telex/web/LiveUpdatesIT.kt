package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.identity.StartedSession
import telex.identity.internal.owner.Owners
import telex.messaging.AccountLinked
import telex.messaging.AccountUnlinked
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.LinkedAccountStateChanged
import telex.messaging.LinkedAccountSyncProgressed
import telex.shared.Uuid7
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** AC-116, AC-121, AC-122: the SSE stream tells an open page which query to refetch; it never carries Owner data. */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = ["telex.live.heartbeat=PT0.3S", "telex.live.throttle=PT0.5S"],
)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class LiveUpdatesIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var publisher: ApplicationEventPublisher

    @Autowired lateinit var tx: TransactionTemplate

    private val http = HttpClient.newHttpClient()
    private val open = mutableListOf<Stream>()

    /** An open stream read line by line on its own thread. */
    private inner class Stream(
        val response: HttpResponse<InputStream>,
    ) {
        val lines = CopyOnWriteArrayList<String>()

        @Volatile var closed = false

        private val reader =
            Thread {
                runCatching { response.body().bufferedReader().forEachLine { lines += it } }
                closed = true
            }.apply {
                isDaemon = true
                start()
            }

        fun hints(): Int = lines.count { it == "data: linked-accounts" }

        fun close() {
            runCatching { response.body().close() }
            reader.interrupt()
        }
    }

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    @AfterEach
    fun closeStreams() {
        open.forEach { it.close() }
        open.clear()
    }

    private fun owner(email: String): OwnerId = owners.findOrCreate(email, email, clock.instant()).first

    private fun start(owner: OwnerId): StartedSession = sessions.start(owner, null, "Firefox/130.0", "UTC", false)

    private fun request(key: String?): HttpRequest =
        HttpRequest
            .newBuilder(URI.create("http://localhost:$port/api/v1/live-updates"))
            .also { b -> key?.let { b.header("Cookie", "telex_session=$it") } }
            .build()

    private fun stream(key: String): Stream {
        val response = http.send(request(key), HttpResponse.BodyHandlers.ofInputStream())
        return Stream(response).also { open += it }
    }

    private fun publish(event: Any) {
        tx.executeWithoutResult { publisher.publishEvent(event) }
    }

    private fun awaitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(25)
    }

    private fun account() = LinkedAccountId(Uuid7.next())

    @Test
    fun `an open stream is event-stream with no-store and no proxy buffering`() {
        val s = stream(start(owner("anton@mail.com")).key)

        assertThat(s.response.statusCode()).isEqualTo(200)
        assertThat(
            s.response
                .headers()
                .firstValue("Content-Type")
                .orElse(""),
        ).startsWith("text/event-stream")
        assertThat(s.response.headers().firstValue("Cache-Control")).hasValue("no-store")
        assertThat(s.response.headers().firstValue("X-Accel-Buffering")).hasValue("no")
    }

    @Test
    fun `without a session cookie the stream is 401`() {
        val r = http.send(request(null), HttpResponse.BodyHandlers.ofString())

        assertThat(r.statusCode()).isEqualTo(401)
    }

    @Test
    fun `AC-116 AC-121 AC-122 each event type sends a linked-accounts hint to its Owner only`() {
        val anton = owner("anton@mail.com")
        val other = owner("other@mail.com")
        val mine = stream(start(anton).key)
        val theirs = stream(start(other).key)
        val events =
            listOf<Any>(
                AccountLinked(anton, account()),
                AccountUnlinked(anton, account()),
                LinkedAccountStateChanged(anton, account(), LinkedAccountState.RECONNECTING),
                LinkedAccountSyncProgressed(anton, account()),
            )

        events.forEachIndexed { i, event ->
            publish(event)
            awaitUntil { mine.hints() == i + 1 }
            assertThat(mine.hints()).describedAs(event::class.simpleName).isEqualTo(i + 1)
            assertThat(mine.lines).contains("event: hint")
            Thread.sleep(600)
        }
        assertThat(theirs.hints()).isZero()
    }

    @Test
    fun `a burst of 50 sync progress events sends at most a couple of hints and ends with the last`() {
        val anton = owner("anton@mail.com")
        val s = stream(start(anton).key)
        val id = account()

        repeat(50) { publish(LinkedAccountSyncProgressed(anton, id)) }
        Thread.sleep(1500)

        assertThat(s.hints()).isBetween(2, 4)
    }

    @Test
    fun `a heartbeat comment arrives and the stream carries only hint names and comments`() {
        val anton = owner("anton@mail.com")
        val s = stream(start(anton).key)
        publish(AccountLinked(anton, account()))

        awaitUntil { s.lines.count { it == ": keep-alive" } >= 2 && s.hints() >= 1 }

        assertThat(s.lines.count { it == ": keep-alive" }).isGreaterThanOrEqualTo(2)
        assertThat(s.lines.filter { it.isNotBlank() }).allMatch {
            it == "event: hint" || it == "data: linked-accounts" || it.startsWith(": ")
        }
    }

    @Test
    fun `the stream never bumps Sign-in Session activity`() {
        val s = start(owner("anton@mail.com"))
        val started = lastActivity(s)
        clock.advance(Duration.ofMinutes(10))

        stream(s.key)
        Thread.sleep(800)

        assertThat(lastActivity(s)).isEqualTo(started)
    }

    @Test
    fun `the stream closes once its Sign-in Session ends`() {
        val anton = owner("anton@mail.com")
        val session = start(anton)
        val s = stream(session.key)

        sessions.endMine(anton, session.sessionId)
        awaitUntil { s.closed }

        assertThat(s.closed).isTrue()
    }

    private fun lastActivity(s: StartedSession): Instant =
        jdbc
            .queryForObject(
                "SELECT last_activity_at FROM sign_in_session WHERE id = ?",
                Timestamp::class.java,
                s.sessionId.value,
            )!!
            .toInstant()
}
