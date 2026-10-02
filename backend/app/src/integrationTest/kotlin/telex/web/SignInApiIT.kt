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
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/** AC-34, AC-82, AC-84, AC-85, AC-86, AC-103, AC-95: the sign-in REST endpoints. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
class SignInApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val http = HttpClient.newHttpClient()
    private val start = Instant.parse("2026-10-02T14:00:00Z")

    @BeforeEach
    fun reset() {
        clock.set(start)
        mailer.reset()
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM sign_in_grant")
        jdbc.execute("DELETE FROM owner")
    }

    private fun post(
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        cookie: String? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "csrf")
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", cookie?.let { "telex_session=$it" }).joinToString("; ")
        builder.header("Cookie", cookies)
        headers.forEach { (k, v) -> builder.header(k, v) }
        builder.POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(
            "POST",
            path,
            body,
            mapOf("Content-Type" to "application/json", "X-XSRF-TOKEN" to "csrf") + headers,
            response,
        )
        return response
    }

    private class Mail(
        val grantId: String,
        val token: String,
        val code: String,
    )

    private fun requestMail(email: String = "anton@mail.com"): Mail {
        val r = post("/api/v1/sign-in/grants", """{"email":"$email"}""")
        assertThat(r.statusCode()).isEqualTo(201)
        val text = mailer.sent.last().text
        return Mail(
            Regex("\"grantId\":\"([^\"]+)\"").find(r.body())!!.groupValues[1],
            Regex("#([A-Za-z0-9_-]+)").find(text)!!.groupValues[1],
            Regex("\\b(\\d{6})\\b").find(text)!!.groupValues[1],
        )
    }

    private fun redeemLink(
        token: String,
        cookie: String? = null,
    ) = post(
        "/api/v1/sign-in/link/redeem",
        """{"linkToken":"$token"}""",
        mapOf("X-Telex-Time-Zone" to "Europe/Kyiv"),
        cookie,
    )

    private fun redeemCode(
        grantId: String,
        code: String,
    ) = post("/api/v1/sign-in/grants/$grantId/code", """{"code":"$code"}""")

    private fun sessionCookie(r: HttpResponse<String>) =
        r.headers().allValues("Set-Cookie").first { it.startsWith("telex_session=") }

    private fun keyOf(r: HttpResponse<String>) = sessionCookie(r).substringAfter("=").substringBefore(";")

    private fun wrong(code: String) = if (code == "000000") "000001" else "000000"

    @Test
    fun `AC-34 request returns 201 with grantId and typed email, and mails the address as typed`() {
        val r = post("/api/v1/sign-in/grants", """{"email":"Anton+work@Mail.com"}""")

        assertThat(r.statusCode()).isEqualTo(201)
        assertThat(r.body()).contains("\"email\":\"Anton+work@Mail.com\"").contains("\"grantId\"")
        assertThat(mailer.sent.last().to).isEqualTo("Anton+work@Mail.com")
        assertThat(r.headers().firstValue("Cache-Control")).hasValueSatisfying { assertThat(it).contains("no-store") }
    }

    @Test
    fun `AC-34 incomplete email is 400 validation-failed with an email field error`() {
        val r = post("/api/v1/sign-in/grants", """{"email":"anton@"}""")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"validation-failed\"").contains("email-incomplete")
    }

    @Test
    fun `AC-34 mail failure is 503 mail-unavailable`() {
        mailer.failing = true
        val r = post("/api/v1/sign-in/grants", """{"email":"anton@mail.com"}""")

        assertThat(r.statusCode()).isEqualTo(503)
        assertThat(r.body()).contains("\"code\":\"mail-unavailable\"")
    }

    @Test
    fun `AC-34 link redeem creates the account and sets the session cookie, same address reuses it`() {
        val mail = requestMail()
        val r = redeemLink(mail.token)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"createdAccount\":true")
        assertThat(sessionCookie(r)).contains("Max-Age=7776000", "Path=/", "HttpOnly", "SameSite=Lax")
        assertThat(r.body()).doesNotContain(mail.token).doesNotContain(keyOf(r))

        val again = redeemLink(requestMail("ANTON+x@mail.com").token)
        assertThat(again.body()).contains("\"createdAccount\":false")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM owner", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `AC-86 preview returns the email and changes nothing, a later confirm signs in`() {
        val mail = requestMail()
        val p1 = post("/api/v1/sign-in/link/preview", """{"linkToken":"${mail.token}"}""")
        val p2 = post("/api/v1/sign-in/link/preview", """{"linkToken":"${mail.token}"}""")

        assertThat(p1.statusCode()).isEqualTo(200)
        assertThat(p1.body()).contains("\"email\":\"anton@mail.com\"").doesNotContain(mail.token)
        assertThat(p2.statusCode()).isEqualTo(200)
        clock.set(start.plus(Duration.ofMinutes(14)))
        assertThat(redeemLink(mail.token).statusCode()).isEqualTo(200)
    }

    @Test
    fun `AC-82 code redeem signs in and then the link is refused as used`() {
        val mail = requestMail()
        val r = redeemCode(mail.grantId, mail.code)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(sessionCookie(r)).contains("HttpOnly")
        assertThat(r.body()).doesNotContain(mail.code)
        val link = redeemLink(mail.token)
        assertThat(link.statusCode()).isEqualTo(410)
        assertThat(link.body()).contains("\"code\":\"sign-in-link-used\"")
    }

    @Test
    fun `AC-84 reusing a redeemed link or code is 410 sign-in-link-used with the email`() {
        val mail = requestMail()
        redeemLink(mail.token)

        val link = redeemLink(mail.token)
        val code = redeemCode(mail.grantId, mail.code)
        val preview = post("/api/v1/sign-in/link/preview", """{"linkToken":"${mail.token}"}""")

        listOf(link, code, preview).forEach {
            assertThat(it.statusCode()).isEqualTo(410)
            assertThat(it.body()).contains("\"code\":\"sign-in-link-used\"").contains("\"email\":\"anton@mail.com\"")
        }
    }

    @Test
    fun `AC-85 wrong codes answer 422 with attemptsLeft, the fifth voids the grant, the link is refused`() {
        val mail = requestMail()
        val first = redeemCode(mail.grantId, wrong(mail.code))
        assertThat(first.statusCode()).isEqualTo(422)
        assertThat(first.body()).contains("\"code\":\"sign-in-code-wrong\"").contains("\"attemptsLeft\":4")
        repeat(3) { redeemCode(mail.grantId, wrong(mail.code)) }
        val fifth = redeemCode(mail.grantId, wrong(mail.code))
        assertThat(fifth.statusCode()).isEqualTo(410)
        assertThat(fifth.body()).contains("\"code\":\"sign-in-grant-void\"")

        assertThat(redeemCode(mail.grantId, mail.code).statusCode()).isEqualTo(410)
        val link = redeemLink(mail.token)
        assertThat(link.statusCode()).isEqualTo(410)
        assertThat(link.body()).contains("\"code\":\"sign-in-grant-void\"")
    }

    @Test
    fun `malformed code is 400 code-format and does not count as an attempt`() {
        val mail = requestMail()
        listOf("12345", "abcdef").forEach {
            val r = redeemCode(mail.grantId, it)
            assertThat(r.statusCode()).isEqualTo(400)
            assertThat(r.body()).contains("\"code\":\"validation-failed\"").contains("code-format")
        }
        assertThat(jdbc.queryForObject("SELECT sum(wrong_attempts) FROM sign_in_grant", Int::class.java)).isZero()
    }

    @Test
    fun `grantId that is not a UUID is 400 validation-failed`() {
        val r = redeemCode("not-a-uuid", "123456")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"validation-failed\"")
    }

    @Test
    fun `AC-103 an older email is refused as expired once a newer one was requested`() {
        val older = requestMail()
        requestMail()

        val link = redeemLink(older.token)
        val code = redeemCode(older.grantId, older.code)

        assertThat(link.statusCode()).isEqualTo(410)
        assertThat(link.body()).contains("\"code\":\"sign-in-link-expired\"")
        assertThat(code.statusCode()).isEqualTo(410)
        assertThat(code.body()).contains("\"code\":\"sign-in-link-expired\"")
    }

    @Test
    fun `unknown link token is 410 without an email`() {
        val r = redeemLink("nope")

        assertThat(r.statusCode()).isEqualTo(410)
        assertThat(r.body()).doesNotContain("\"email\"")
    }

    @Test
    fun `AC-95 sign out ends the session, clears the cookie, and the next API call is unauthenticated`() {
        val key = keyOf(redeemLink(requestMail().token))

        val out = post("/api/v1/sign-out", cookie = key)

        assertThat(out.statusCode()).isEqualTo(204)
        assertThat(sessionCookie(out)).contains("telex_session=;", "Max-Age=0")
        assertThat(out.headers().firstValue("Cache-Control")).hasValueSatisfying { assertThat(it).contains("no-store") }
        val me =
            http.send(
                HttpRequest
                    .newBuilder(URI.create("http://localhost:$port/api/v1/me"))
                    .header("Cookie", "telex_session=$key")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertThat(me.statusCode()).isEqualTo(401)
    }

    @Test
    fun `AC-95 sign out without a cookie is still 204 and clears the cookie`() {
        val out = post("/api/v1/sign-out")

        assertThat(out.statusCode()).isEqualTo(204)
        assertThat(sessionCookie(out)).contains("Max-Age=0")
    }
}
