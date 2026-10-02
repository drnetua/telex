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
import java.time.Duration
import java.time.Instant

/** AC-93, AC-94, AC-97, AC-100: list / end / end-others / who-am-I, Owner-scoped. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class SessionsApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val http = HttpClient.newHttpClient()
    private val laptopUa = "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0"
    private val phoneUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605 Version/17.0 Mobile Safari/604.1"

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun owner(
        email: String,
        canonical: String = email,
    ): OwnerId = owners.findOrCreate(email, canonical, clock.instant()).first

    private fun start(
        owner: OwnerId,
        ua: String = laptopUa,
    ): StartedSession = sessions.start(owner, null, ua, "Europe/Kyiv", false)

    private fun call(
        method: String,
        path: String,
        key: String?,
        background: Boolean = false,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies).header("X-XSRF-TOKEN", "csrf")
        if (background) b.header("X-Telex-Background", "1")
        b.method(method, HttpRequest.BodyPublishers.noBody())
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        val headers =
            mapOf("X-XSRF-TOKEN" to "csrf") + if (background) mapOf("X-Telex-Background" to "1") else emptyMap()
        ContractValidator.assertConforms(method, path, null, headers, response)
        return response
    }

    private fun ids(r: HttpResponse<String>) =
        Regex("\"id\":\"([^\"]+)\"")
            .findAll(r.body())
            .map {
                it.groupValues[1]
            }.toList()

    @Test
    fun `AC-93 list shows browser, device type, last activity, current flagged, newest first`() {
        val o = owner("anton@mail.com")
        val laptop = start(o)
        clock.advance(Duration.ofMinutes(5))
        val phone = start(o, phoneUa)

        val r = call("GET", "/api/v1/sessions", laptop.key, background = true)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(ids(r)).containsExactly(phone.sessionId.value.toString(), laptop.sessionId.value.toString())
        assertThat(r.body())
            .contains("\"userAgentLabel\":\"Safari on iPhone\"", "\"deviceType\":\"phone\"")
            .contains("\"userAgentLabel\":\"Firefox on Linux\"", "\"deviceType\":\"computer\"")
            .contains("\"startedAt\"", "\"lastActivityAt\"")
        assertThat(Regex("\"current\":true").findAll(r.body()).count()).isEqualTo(1)
        assertThat(r.body().substringAfter(laptop.sessionId.value.toString())).contains("\"current\":true")
    }

    @Test
    fun `AC-93 ending the phone session makes its next request 401 session-ended`() {
        val o = owner("anton@mail.com")
        val laptop = start(o)
        val phone = start(o, phoneUa)

        val end = call("DELETE", "/api/v1/sessions/${phone.sessionId.value}", laptop.key)

        assertThat(end.statusCode()).isEqualTo(204)
        val next = call("GET", "/api/v1/me", phone.key)
        assertThat(next.statusCode()).isEqualTo(401)
        assertThat(next.body()).contains("\"code\":\"session-ended\"")
        assertThat(ids(call("GET", "/api/v1/sessions", laptop.key))).containsExactly(laptop.sessionId.value.toString())
    }

    @Test
    fun `ending the current session is 204 and the next request is session-ended`() {
        val s = start(owner("anton@mail.com"))

        assertThat(call("DELETE", "/api/v1/sessions/${s.sessionId.value}", s.key).statusCode()).isEqualTo(204)

        val next = call("GET", "/api/v1/me", s.key)
        assertThat(next.statusCode()).isEqualTo(401)
        assertThat(next.body()).contains("\"code\":\"session-ended\"")
    }

    @Test
    fun `AC-94 end-others ends every other session and leaves only this device`() {
        val o = owner("anton@mail.com")
        val laptop = start(o)
        val phone = start(o, phoneUa)
        val tablet = start(o, phoneUa)

        val r = call("POST", "/api/v1/sessions/end-others", laptop.key)

        assertThat(r.statusCode()).isEqualTo(204)
        val list = call("GET", "/api/v1/sessions", laptop.key)
        assertThat(ids(list)).containsExactly(laptop.sessionId.value.toString())
        assertThat(list.body()).contains("\"current\":true")
        assertThat(call("GET", "/api/v1/me", phone.key).statusCode()).isEqualTo(401)
        assertThat(call("GET", "/api/v1/me", tablet.key).statusCode()).isEqualTo(401)
    }

    @Test
    fun `end-others with only the current session is 204 and changes nothing`() {
        val s = start(owner("anton@mail.com"))

        assertThat(call("POST", "/api/v1/sessions/end-others", s.key).statusCode()).isEqualTo(204)
        assertThat(ids(call("GET", "/api/v1/sessions", s.key))).containsExactly(s.sessionId.value.toString())
    }

    @Test
    fun `AC-97 another owner's session is not listed, not endable, and looks missing`() {
        val a = start(owner("a@mail.com"))
        val b = start(owner("b@mail.com"), phoneUa)

        assertThat(ids(call("GET", "/api/v1/sessions", a.key))).containsExactly(a.sessionId.value.toString())
        val foreign = call("DELETE", "/api/v1/sessions/${b.sessionId.value}", a.key)
        val random = call("DELETE", "/api/v1/sessions/${java.util.UUID.randomUUID()}", a.key)

        assertThat(foreign.statusCode()).isEqualTo(404)
        assertThat(foreign.body()).contains("\"code\":\"not-found\"")
        assertThat(random.statusCode()).isEqualTo(404)
        assertThat(random.body()).contains("\"code\":\"not-found\"")
        assertThat(call("GET", "/api/v1/me", b.key).statusCode()).isEqualTo(200)
        call("POST", "/api/v1/sessions/end-others", a.key)
        assertThat(call("GET", "/api/v1/me", b.key).statusCode()).isEqualTo(200)
    }

    @Test
    fun `deleting an already ended session is 404 not-found`() {
        val o = owner("anton@mail.com")
        val laptop = start(o)
        val phone = start(o, phoneUa)
        call("DELETE", "/api/v1/sessions/${phone.sessionId.value}", laptop.key)

        val again = call("DELETE", "/api/v1/sessions/${phone.sessionId.value}", laptop.key)

        assertThat(again.statusCode()).isEqualTo(404)
        assertThat(again.body()).contains("\"code\":\"not-found\"")
    }

    @Test
    fun `idle-expired sessions of the owner are not listed`() {
        val o = owner("anton@mail.com")
        val old = start(o, phoneUa)
        clock.advance(Duration.ofDays(31))
        val fresh = start(o)

        assertThat(ids(call("GET", "/api/v1/sessions", fresh.key))).containsExactly(fresh.sessionId.value.toString())
        assertThat(old.sessionId).isNotNull()
    }

    @Test
    fun `AC-100 me returns the owner id, the address as created, and zero linked accounts`() {
        val o = owner("Anton@Mail.com", "anton@mail.com")
        val s = start(o)

        val r = call("GET", "/api/v1/me", s.key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body())
            .contains("\"ownerId\":\"${o.value}\"", "\"email\":\"Anton@Mail.com\"", "\"linkedAccountCount\":0")
    }

    @Test
    fun `endpoints without a session are 401`() {
        assertThat(call("GET", "/api/v1/me", null).statusCode()).isEqualTo(401)
        assertThat(call("GET", "/api/v1/sessions", null).statusCode()).isEqualTo(401)
    }
}
