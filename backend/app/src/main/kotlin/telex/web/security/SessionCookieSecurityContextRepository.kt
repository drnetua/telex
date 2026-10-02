package telex.web.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.DeferredSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpRequestResponseHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import telex.identity.SessionResolution
import telex.identity.SignInSessions

/** Stateless: the Sign-in Session behind the `telex_session` cookie is the security context (ADR-0001). */
@Component
class SessionCookieSecurityContextRepository(
    private val sessions: SignInSessions,
    private val cookies: SessionCookies,
) : SecurityContextRepository {
    override fun loadDeferredContext(request: HttpServletRequest): DeferredSecurityContext =
        object : DeferredSecurityContext {
            private val context: SecurityContext by lazy { resolve(request) }

            override fun get(): SecurityContext = context

            override fun isGenerated(): Boolean = false
        }

    @Deprecated("Superseded by loadDeferredContext")
    override fun loadContext(requestResponseHolder: HttpRequestResponseHolder): SecurityContext =
        loadDeferredContext(requestResponseHolder.request).get()

    override fun saveContext(
        context: SecurityContext,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = Unit

    override fun containsContext(request: HttpServletRequest): Boolean = cookies.read(request) != null

    private fun resolve(request: HttpServletRequest): SecurityContext {
        val context = SecurityContextHolder.createEmptyContext()
        val key = cookies.read(request) ?: return context
        val background = request.getHeader(BACKGROUND_HEADER) == "1"
        when (val resolution = sessions.resolve(key, background)) {
            is SessionResolution.Live -> {
                context.authentication =
                    UsernamePasswordAuthenticationToken.authenticated(resolution.ownerId, null, emptyList())
            }

            SessionResolution.Ended -> {
                request.setAttribute(SESSION_ENDED, true)
            }

            SessionResolution.Unknown -> {
                Unit
            }
        }
        return context
    }

    companion object {
        const val SESSION_ENDED = "telex.sessionEnded"
        const val BACKGROUND_HEADER = "X-Telex-Background"
    }
}
