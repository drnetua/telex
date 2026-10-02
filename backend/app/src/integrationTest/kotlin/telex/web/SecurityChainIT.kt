package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import telex.TestcontainersConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** AC-101, AC-96, AC-95 (server half) and the 401/403 problem format. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class SecurityChainIT(
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `protected API without a cookie is a 401 unauthenticated problem with no-store`() {
        val response = send(request("/api/v1/me").GET().build())

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json")
        assertThat(
            response.headers().firstValue("Cache-Control"),
        ).hasValueSatisfying { assertThat(it).contains("no-store") }
        assertThat(response.body()).contains("\"code\":\"unauthenticated\"")
        assertThat(response.body()).contains("urn:telex:error:unauthenticated")
    }

    @Test
    fun `unknown session cookie is a 401 unauthenticated problem`() {
        val response = send(request("/api/v1/me").header("Cookie", "telex_session=nope").GET().build())

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.body()).contains("\"code\":\"unauthenticated\"")
    }

    @Test
    fun `state-changing request without the CSRF header is a 403 forbidden problem`() {
        val response =
            send(
                request("/api/v1/sign-in/email")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(EMAIL_BODY))
                    .build(),
                EMAIL_BODY,
            )

        assertThat(response.statusCode()).isEqualTo(403)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json")
        assertThat(response.body()).contains("\"code\":\"forbidden\"")
    }

    @Test
    fun `client routes stay public and the SPA index sets the readable XSRF-TOKEN cookie`() {
        val response = send(request("/inbox").GET().build())

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().allValues("Set-Cookie")).anySatisfy {
            assertThat(it).startsWith("XSRF-TOKEN=")
            assertThat(it).doesNotContain("HttpOnly")
        }
    }

    @Test
    fun `actuator health is public`() {
        assertThat(send(request("/actuator/health").GET().build()).statusCode()).isEqualTo(200)
    }

    @Test
    fun `webauthn paths are not swallowed by the SPA fallback`() {
        val response = send(request("/webauthn/register/options").GET().build())

        assertThat(response.body()).doesNotContain("""<div id="root"></div>""")
    }

    private fun request(path: String) = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))

    /** Sends the request; API exchanges are also checked against openapi.yaml. */
    private fun send(
        request: HttpRequest,
        body: String? = null,
    ): HttpResponse<String> {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val path = request.uri().rawPath + (request.uri().rawQuery?.let { "?$it" } ?: "")
        if (path.startsWith("/api/")) {
            val headers = request.headers().map().mapValues { it.value.first() }
            ContractValidator.assertConforms(request.method(), path, body, headers, response)
        }
        return response
    }

    private companion object {
        const val EMAIL_BODY = """{"email":"me@example.com"}"""
    }
}
