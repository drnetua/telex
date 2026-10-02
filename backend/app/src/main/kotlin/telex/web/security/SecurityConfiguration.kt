package telex.web.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.web.filter.OncePerRequestFilter
import telex.shared.problemDetail
import tools.jackson.databind.json.JsonMapper

/** The one filter chain: a live Sign-in Session or nothing, CSRF cookie on every response, problem+json refusals. */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        repository: SessionCookieSecurityContextRepository,
        json: JsonMapper,
    ): SecurityFilterChain {
        http {
            securityContext { securityContextRepository = repository }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
            }
            authorizeHttpRequests {
                authorize("/api/v1/sign-in/**", permitAll)
                authorize("/api/v1/sign-out", permitAll)
                authorize("/webauthn/authenticate/options", permitAll)
                authorize("/login/webauthn", permitAll)
                authorize("/api/**", authenticated)
                authorize("/webauthn/**", authenticated)
                authorize(anyRequest, permitAll)
            }
            exceptionHandling {
                authenticationEntryPoint =
                    AuthenticationEntryPoint { request, response, _ ->
                        val ended = request.getAttribute(SessionCookieSecurityContextRepository.SESSION_ENDED) == true
                        if (ended) {
                            write(
                                json,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "session-ended",
                                "Your sign-in session has ended.",
                            )
                        } else {
                            write(json, response, HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.")
                        }
                    }
                accessDeniedHandler =
                    AccessDeniedHandler { _, response, _ ->
                        write(json, response, HttpStatus.FORBIDDEN, "forbidden", "This request was refused.")
                    }
            }
            formLogin { disable() }
            httpBasic { disable() }
            logout { disable() }
            requestCache { requestCache = NullRequestCache() }
            headers { cacheControl { } }
        }
        http.addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
        return http.build()
    }

    private fun write(
        json: JsonMapper,
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        detail: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.outputStream.write(json.writeValueAsBytes(problemDetail(status, code, detail)))
    }

    /** Forces the deferred CSRF token to load so the readable `XSRF-TOKEN` cookie is set on every response. */
    private class CsrfCookieFilter : OncePerRequestFilter() {
        override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            chain: FilterChain,
        ) {
            (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
            chain.doFilter(request, response)
        }
    }
}
