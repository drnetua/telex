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

/** Over real HTTP, so the welcome-page forward for `/` runs as it does for a browser. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class SpaHostingIT(
    @LocalServerPort private val port: Int,
) {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `root serves the SPA index`() {
        val response = get("/")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(
            response.headers().firstValue("Content-Type"),
        ).hasValueSatisfying { assertThat(it).startsWith("text/html") }
        assertThat(response.body()).contains(SPA_ROOT)
    }

    @Test
    fun `client-side routes fall back to the SPA index`() {
        val response = get("/agents/42/runs")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains(SPA_ROOT)
    }

    @Test
    fun `missing assets stay 404 problems`() {
        val response = get("/assets/missing.js")

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json")
        assertThat(response.body()).contains("\"code\":\"not-found\"")
    }

    @Test
    fun `API paths are never the SPA index, a signed-out caller gets a 401 problem`() {
        val response = get("/api/missing")

        assertThat(response.statusCode()).isEqualTo(401)
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json")
        assertThat(response.body()).doesNotContain(SPA_ROOT)
    }

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private companion object {
        const val SPA_ROOT = """<div id="root"></div>"""
    }
}
