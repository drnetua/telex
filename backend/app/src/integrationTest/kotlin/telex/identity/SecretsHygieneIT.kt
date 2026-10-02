package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Sad §6.1 / QG-1d: the raw link token, code and session cookie never reach logs or the event registry. */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "logging.level.telex=DEBUG",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
    ],
)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class SecretsHygieneIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var jdbc: JdbcTemplate

    private val http = HttpClient.newHttpClient()

    private fun post(
        path: String,
        body: String? = null,
        cookie: String? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "csrf")
                .header(
                    "Cookie",
                    listOfNotNull("XSRF-TOKEN=csrf", cookie?.let { "telex_session=$it" }).joinToString("; "),
                ).POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun sessionKey(r: HttpResponse<String>) =
        r
            .headers()
            .allValues(
                "Set-Cookie",
            ).first { it.startsWith("telex_session=") }
            .substringAfter("=")
            .substringBefore(";")

    @Test
    fun `request, redeem by link and by code, and sign-out leave no raw secret in logs or event_publication`(
        output: CapturedOutput,
    ) {
        mailer.reset()
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM sign_in_grant")
        jdbc.execute("DELETE FROM event_publication")
        val secrets = mutableListOf<String>()

        val first = post("/api/v1/sign-in/grants", """{"email":"hygiene@mail.com"}""")
        val token = Regex("#([A-Za-z0-9_-]+)").find(mailer.sent.last().text)!!.groupValues[1]
        val link = post("/api/v1/sign-in/link/redeem", """{"linkToken":"$token"}""")
        val linkKey = sessionKey(link)
        secrets += listOf(token, linkKey)

        val second = post("/api/v1/sign-in/grants", """{"email":"hygiene@mail.com"}""")
        val grantId = Regex("\"grantId\":\"([^\"]+)\"").find(second.body())!!.groupValues[1]
        val code = Regex("\\b(\\d{6})\\b").find(mailer.sent.last().text)!!.groupValues[1]
        val byCode = post("/api/v1/sign-in/grants/$grantId/code", """{"code":"$code"}""", linkKey)
        val codeKey = sessionKey(byCode)
        secrets += listOf(code, codeKey)

        assertThat(post("/api/v1/sign-out", cookie = codeKey).statusCode()).isEqualTo(204)
        assertThat(first.statusCode()).isEqualTo(201)

        val captured = output.all
        val events =
            jdbc.queryForList("SELECT serialized_event FROM event_publication", String::class.java).joinToString("\n")
        assertThat(events).describedAs("a session-started event was recorded").contains("sessionId")
        secrets.forEach {
            assertThat(captured).describedAs("logs must not contain a raw secret").doesNotContain(it)
            assertThat(events).describedAs("event_publication must not contain a raw secret").doesNotContain(it)
        }
    }
}
