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
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/** AC-179, AC-182, AC-183, AC-184, AC-186: me with preferences, PATCH, detected zone, zone list. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PreferencesApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val http = HttpClient.newHttpClient()
    private val prefs = "/api/v1/me/preferences"
    private val detected = "/api/v1/me/preferences/detected-time-zone"

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun signedIn(email: String = "anton@mail.com"): Pair<OwnerId, StartedSession> {
        val id = owners.findOrCreate(email, email, clock.instant()).first
        return id to sessions.start(id, null, "Mozilla/5.0 (X11; Linux x86_64) Firefox/130.0", "Europe/Kyiv", false)
    }

    private fun call(
        method: String,
        path: String,
        key: String?,
        body: String? = null,
        csrf: Boolean = true,
        background: Boolean = false,
    ): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        b.header("Cookie", cookies)
        val headers = mutableMapOf<String, String>()
        if (csrf) headers["X-XSRF-TOKEN"] = "csrf"
        if (background) headers["X-Telex-Background"] = "1"
        if (body != null) headers["Content-Type"] = "application/json"
        headers.forEach { (k, v) -> b.header(k, v) }
        b.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        val response = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(method, path, body, headers, response, ContractValidator.APP_SHELL_SPEC)
        return response
    }

    private fun stored(owner: OwnerId) = checkNotNull(owners.preferencesOf(owner))

    private fun detect(
        key: String?,
        zone: String?,
        csrf: Boolean = true,
    ) = call("POST", detected, key, """{"timeZone":${zone?.let { "\"$it\"" }}}""", csrf, background = true)

    @Test
    fun `getMe carries theme, timeZone and timeZoneIsFallback, null until saved`() {
        val (_, s) = signedIn()

        val r = call("GET", "/api/v1/me", s.key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store")
        assertThat(r.body())
            .contains("\"theme\":\"system\"", "\"timeZone\":null", "\"timeZoneIsFallback\":false")
            .contains("\"linkedAccountCount\":0")
    }

    @Test
    fun `getMe without a session is 401`() {
        assertThat(call("GET", "/api/v1/me", null).statusCode()).isEqualTo(401)
    }

    @Test
    fun `AC-179 PATCH theme saves it and answers the preferences`() {
        val (o, s) = signedIn()
        detect(s.key, "Europe/Kyiv")

        val r = call("PATCH", prefs, s.key, """{"theme":"dark"}""")

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"theme\":\"dark\"")
        assertThat(stored(o).theme.wire).isEqualTo("dark")
    }

    @Test
    fun `AC-184 PATCH timeZone saves it and clears the fallback flag`() {
        val (o, s) = signedIn()
        detect(s.key, null)
        assertThat(stored(o).timeZoneIsFallback).isTrue()

        val r = call("PATCH", prefs, s.key, """{"timeZone":"America/Argentina/Buenos_Aires"}""")

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"timeZone\":\"America/Argentina/Buenos_Aires\"", "\"timeZoneIsFallback\":false")
        assertThat(stored(o).timeZone).isEqualTo("America/Argentina/Buenos_Aires")
    }

    @Test
    fun `PATCH with an empty body answers the current preferences`() {
        val (_, s) = signedIn()
        detect(s.key, "Europe/Kyiv")

        val r = call("PATCH", prefs, s.key, "{}")

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"theme\":\"system\"", "\"timeZone\":\"Europe/Kyiv\"")
    }

    @Test
    fun `AC-186 PATCH timeZone null or empty is time-zone-required and keeps the zone`() {
        val (o, s) = signedIn()
        call("PATCH", prefs, s.key, """{"timeZone":"Europe/Kyiv"}""")

        listOf("""{"timeZone":null}""", """{"timeZone":""}""").forEach {
            val r = call("PATCH", prefs, s.key, it)
            assertThat(r.statusCode()).isEqualTo(400)
            assertThat(r.body())
                .contains("\"code\":\"validation-failed\"", "\"field\":\"timeZone\"", "\"code\":\"time-zone-required\"")
                .contains("Choose a timezone from the list.")
        }
        assertThat(stored(o).timeZone).isEqualTo("Europe/Kyiv")
    }

    @Test
    fun `PATCH unknown timeZone is unknown-time-zone and keeps the zone`() {
        val (o, s) = signedIn()
        call("PATCH", prefs, s.key, """{"timeZone":"Europe/Kyiv"}""")

        val r = call("PATCH", prefs, s.key, """{"timeZone":"Europe/Atlantis"}""")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"unknown-time-zone\"", "\"field\":\"timeZone\"")
        assertThat(stored(o).timeZone).isEqualTo("Europe/Kyiv")
    }

    @Test
    fun `AC-182 PATCH unknown theme is unknown-theme and keeps the theme`() {
        val (o, s) = signedIn()
        call("PATCH", prefs, s.key, """{"theme":"light"}""")

        val r = call("PATCH", prefs, s.key, """{"theme":"blue"}""")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body())
            .contains("\"code\":\"unknown-theme\"", "\"field\":\"theme\"", "Choose light, dark or system.")
        assertThat(stored(o).theme.wire).isEqualTo("light")
    }

    @Test
    fun `a refused timezone leaves a valid theme in the same request unsaved`() {
        val (o, s) = signedIn()

        val r = call("PATCH", prefs, s.key, """{"theme":"dark","timeZone":"Nope/Nope"}""")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(stored(o).theme.wire).isEqualTo("system")
    }

    @Test
    fun `PATCH without the CSRF header is 403 and without a session 401`() {
        val (o, s) = signedIn()

        val noCsrf = call("PATCH", prefs, s.key, """{"theme":"dark"}""", csrf = false)
        val anon = call("PATCH", prefs, null, """{"theme":"dark"}""")

        assertThat(noCsrf.statusCode()).isEqualTo(403)
        assertThat(noCsrf.body()).contains("\"code\":\"forbidden\"")
        assertThat(anon.statusCode()).isEqualTo(401)
        assertThat(stored(o).theme.wire).isEqualTo("system")
    }

    @Test
    fun `AC-183 detected zone is saved when unset and a later one changes nothing`() {
        val (o, s) = signedIn()

        val first = detect(s.key, "Europe/Kyiv")
        val second = detect(s.key, "Asia/Tokyo")

        assertThat(first.statusCode()).isEqualTo(200)
        assertThat(first.body()).contains("\"timeZone\":\"Europe/Kyiv\"", "\"timeZoneIsFallback\":false")
        assertThat(second.statusCode()).isEqualTo(200)
        assertThat(second.body()).contains("\"timeZone\":\"Europe/Kyiv\"")
        assertThat(stored(o).timeZone).isEqualTo("Europe/Kyiv")
    }

    @Test
    fun `AC-183 null or unknown detected zone saves UTC as the fallback`() {
        listOf(null, "Mars/Olympus").forEachIndexed { i, zone ->
            val (_, s) = signedIn("fallback$i@mail.com")
            val r = detect(s.key, zone)
            assertThat(r.statusCode()).isEqualTo(200)
            assertThat(r.body()).contains("\"timeZone\":\"UTC\"", "\"timeZoneIsFallback\":true")
        }
    }

    @Test
    fun `detected zone save is 403 without CSRF and 401 without a session`() {
        val (_, s) = signedIn()

        assertThat(detect(s.key, null, csrf = false).statusCode()).isEqualTo(403)
        assertThat(detect(null, null).statusCode()).isEqualTo(401)
    }

    @Test
    fun `a background-marked detected zone save does not bump session activity`() {
        val (_, s) = signedIn()
        val before = lastActivity(s)
        clock.advance(Duration.ofMinutes(10))

        detect(s.key, "Europe/Kyiv")

        assertThat(lastActivity(s)).isEqualTo(before)
    }

    private fun lastActivity(s: StartedSession): Instant =
        jdbc
            .queryForObject(
                "SELECT last_activity_at FROM sign_in_session WHERE id = ?",
                Timestamp::class.java,
                s.sessionId.value,
            )!!
            .toInstant()

    @Test
    fun `listTimeZones answers the sorted whole list and 401 without a session`() {
        val (_, s) = signedIn()

        val r = call("GET", "/api/v1/time-zones", s.key)

        assertThat(r.statusCode()).isEqualTo(200)
        val items =
            Regex(
                "\"([A-Za-z_/+-]+)\"",
            ).findAll(r.body().substringAfter("[")).map { it.groupValues[1] }.toList()
        assertThat(items).contains("Europe/Kyiv", "UTC").isSorted().doesNotHaveDuplicates()
        assertThat(call("GET", "/api/v1/time-zones", null).statusCode()).isEqualTo(401)
    }
}
