package telex.web.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import telex.identity.PublicUrl
import java.time.Duration

/** Reads, writes and clears the `telex_session` cookie; `Secure` follows `TELEX_PUBLIC_URL`. */
@Component
class SessionCookies(
    private val publicUrl: PublicUrl,
) {
    fun read(request: HttpServletRequest): String? = request.cookies?.firstOrNull { it.name == NAME }?.value

    fun write(
        response: HttpServletResponse,
        key: String,
    ) = add(response, key, MAX_AGE)

    fun clear(response: HttpServletResponse) = add(response, "", Duration.ZERO)

    private fun add(
        response: HttpServletResponse,
        value: String,
        maxAge: Duration,
    ) {
        val cookie =
            ResponseCookie
                .from(NAME, value)
                .httpOnly(true)
                .secure(publicUrl.isSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    companion object {
        const val NAME = "telex_session"
        private val MAX_AGE: Duration = Duration.ofDays(90)
    }
}
