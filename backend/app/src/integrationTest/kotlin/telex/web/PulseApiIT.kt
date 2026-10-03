package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.identity.StartedSession
import telex.identity.internal.owner.Owners
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/** Shared plumbing for the pulse ITs: Owners, sessions and contract-checked calls against the app-shell contract. */
abstract class PulseApiSupport(
    private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    protected fun start(email: String): StartedSession {
        val owner: OwnerId = owners.findOrCreate(email, email, clock.instant()).first
        return sessions.start(owner, null, "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0", "Europe/Kyiv", false)
    }

    protected fun lastActivity(s: StartedSession): Instant =
        jdbc
            .queryForObject(
                "SELECT last_activity_at FROM sign_in_session WHERE id = ?",
                Timestamp::class.java,
                s.sessionId.value,
            )!!
            .toInstant()

    protected fun call(
        method: String,
        path: String,
        key: String?,
        body: String? = null,
        background: Boolean = false,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies).header("X-XSRF-TOKEN", "csrf")
        if (background) b.header("X-Telex-Background", "1")
        if (body != null) b.header("Content-Type", "application/json")
        b.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        val headers =
            mapOf("X-XSRF-TOKEN" to "csrf") +
                (if (background) mapOf("X-Telex-Background" to "1") else emptyMap()) +
                (if (body != null) mapOf("Content-Type" to "application/json") else emptyMap())
        ContractValidator.assertConforms(method, path, body, headers, response, ContractValidator.APP_SHELL_SPEC)
        return response
    }

    protected fun pulse(key: String?) = call("GET", "/api/v1/pulse", key, background = true)
}

/** AC-173, AC-174: production pulse answers an empty Inbox and no conditions, and needs a live session. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PulseApiIT(
    @LocalServerPort port: Int,
) : PulseApiSupport(port) {
    @Test
    fun `AC-174 the pulse answers zero and no conditions by default`() {
        val r = pulse(start("anton@mail.com").key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"inboxCount\":0", "\"conditions\":[]")
    }

    @Test
    fun `AC-173 a pulse without a session is 401 unauthenticated and leaks nothing`() {
        val r = pulse(null)

        assertThat(r.statusCode()).isEqualTo(401)
        assertThat(r.body()).contains("\"code\":\"unauthenticated\"").doesNotContain("inboxCount")
    }

    @Test
    fun `AC-173 a pulse on an ended session is 401 session-ended`() {
        val s = start("anton@mail.com")
        clock.advance(Duration.ofDays(31))

        val r = pulse(s.key)

        assertThat(r.statusCode()).isEqualTo(401)
        assertThat(r.body()).contains("\"code\":\"session-ended\"").doesNotContain("inboxCount")
    }

    @Test
    fun `a background pulse never moves last activity`() {
        val s = start("anton@mail.com")
        val started = lastActivity(s)
        clock.advance(Duration.ofMinutes(10))

        assertThat(pulse(s.key).statusCode()).isEqualTo(200)

        assertThat(lastActivity(s)).isEqualTo(started)
    }

    @Test
    fun `the fixture path is 404 not-found when the e2e profile is off`() {
        val s = start("anton@mail.com")

        val r = call("PUT", "/api/v1/e2e-fixtures/pulse", s.key, "{\"inboxCount\":5,\"conditions\":[]}")

        assertThat(r.statusCode()).isEqualTo(404)
        assertThat(r.body()).contains("\"code\":\"not-found\"")
        assertThat(pulse(s.key).body()).contains("\"inboxCount\":0")
    }
}

/** AC-174, AC-175, AC-178: under the `e2e` profile the fixture reports only the calling Owner's values. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("e2e")
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PulseFixtureApiIT(
    @LocalServerPort port: Int,
) : PulseApiSupport(port) {
    private fun fixture(
        key: String,
        body: String,
    ) = call("PUT", "/api/v1/e2e-fixtures/pulse", key, body)

    @Test
    fun `AC-175 one Owner's fixture count is invisible to another Owner`() {
        val a = start("a@mail.com")
        val b = start("b@mail.com")

        assertThat(fixture(a.key, "{\"inboxCount\":5,\"conditions\":[]}").statusCode()).isEqualTo(204)

        assertThat(pulse(a.key).body()).contains("\"inboxCount\":5")
        assertThat(pulse(b.key).body()).contains("\"inboxCount\":0", "\"conditions\":[]")
    }

    @Test
    fun `AC-174 an uncapped count and AC-178 every condition round-trip, each code once`() {
        val s = start("anton@mail.com")

        fixture(s.key, "{\"inboxCount\":104,\"conditions\":[\"account-disconnected\",\"budget-exhausted\"]}")

        val body = pulse(s.key).body()
        assertThat(body).contains("\"inboxCount\":104", "account-disconnected", "budget-exhausted")
        assertThat(Regex("account-disconnected").findAll(body).count()).isEqualTo(1)
    }

    @Test
    fun `a negative count is 400 validation-failed with field code min`() {
        val s = start("anton@mail.com")

        val r = fixture(s.key, "{\"inboxCount\":-1,\"conditions\":[]}")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"validation-failed\"", "\"field\":\"inboxCount\"", "\"code\":\"min\"")
    }

    @Test
    fun `a malformed condition code is 400 validation-failed`() {
        val s = start("anton@mail.com")

        val r = fixture(s.key, "{\"inboxCount\":1,\"conditions\":[\"Not A Code\"]}")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"validation-failed\"")
    }

    @Test
    fun `the fixture endpoint without a session is 401`() {
        val r = call("PUT", "/api/v1/e2e-fixtures/pulse", null, "{\"inboxCount\":1,\"conditions\":[]}")

        assertThat(r.statusCode()).isEqualTo(401)
    }
}
