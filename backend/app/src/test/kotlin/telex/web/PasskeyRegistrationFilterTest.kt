package telex.web

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import telex.web.security.PasskeyRegistrationFilter
import tools.jackson.databind.json.JsonMapper

/** AC-102: only a registration that does not verify is a 400; an infrastructure failure is an honest 5xx. */
class PasskeyRegistrationFilterTest {
    private val filter = PasskeyRegistrationFilter(JsonMapper.builder().build())

    @BeforeEach
    fun signIn() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("owner", "n/a", "ROLE_OWNER")
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun register(failure: Exception): MockHttpServletResponse {
        val request = MockHttpServletRequest("POST", "/webauthn/register").apply { servletPath = "/webauthn/register" }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { _, _ -> throw failure })
        return response
    }

    @Test
    fun `a verification failure is 400 passkey-registration-failed`() {
        val r = register(IllegalArgumentException("Credential with id x already exists"))

        assertThat(r.status).isEqualTo(400)
        assertThat(r.contentAsString).contains("\"code\":\"passkey-registration-failed\"")
    }

    @Test
    fun `a database failure is 503 unavailable, not a 400`() {
        val r = register(DataAccessResourceFailureException("db down"))

        assertThat(r.status).isEqualTo(503)
        assertThat(r.contentAsString).contains("\"code\":\"unavailable\"")
    }
}
