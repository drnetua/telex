package telex.web.security

import com.webauthn4j.util.exception.WebAuthnException
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey
import org.springframework.security.web.webauthn.management.RelyingPartyRegistrationRequest
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import telex.identity.OwnerId
import telex.identity.Passkeys
import telex.identity.PublicUrl
import telex.identity.SignInSessions
import telex.identity.SignedInOwner
import telex.shared.problemDetail
import tools.jackson.databind.json.JsonMapper

/**
 * The framework's relying party, fitted to teleX at the edges: the user entity is named after the Owner, the
 * credential is labelled from the User-Agent, and the credential is discoverable (ADR-0002). No cryptography here.
 */
class TelexRelyingPartyOperations(
    private val delegate: WebAuthnRelyingPartyOperations,
    private val passkeys: Passkeys,
    private val userAgent: () -> String?,
) : WebAuthnRelyingPartyOperations by delegate {
    override fun createPublicKeyCredentialCreationOptions(
        request: PublicKeyCredentialCreationOptionsRequest,
    ): PublicKeyCredentialCreationOptions {
        val owner = (request.authentication.principal as? SignedInOwner)?.ownerId
        if (owner != null) passkeys.ensureUserEntity(owner)
        return delegate.createPublicKeyCredentialCreationOptions(request)
    }

    override fun registerCredential(request: RelyingPartyRegistrationRequest): CredentialRecord =
        passkeys.register {
            delegate.registerCredential(
                ImmutableRelyingPartyRegistrationRequest(
                    request.creationOptions,
                    RelyingPartyPublicKey(request.publicKey.credential, passkeys.labelFor(userAgent())),
                ),
            )
        }

    override fun createCredentialRequestOptions(
        request: PublicKeyCredentialRequestOptionsRequest,
    ): PublicKeyCredentialRequestOptions = delegate.createCredentialRequestOptions(request)

    override fun authenticate(request: RelyingPartyAuthenticationRequest): PublicKeyCredentialUserEntity =
        delegate.authenticate(request)
}

@Configuration(proxyBeanMethods = false)
class PasskeyConfiguration {
    /**
     * The framework resolves the user entity's name to a user before it trusts an assertion; the name is the OwnerId
     * (ADR-0002), so this is the Owner lookup and carries no credentials of its own.
     */
    @Bean
    fun passkeyUserDetails(passkeys: Passkeys): UserDetailsService =
        UserDetailsService { name ->
            val owner = passkeys.existingOwnerOf(name) ?: throw UsernameNotFoundException("No Owner $name")
            User
                .withUsername(
                    owner.value.toString(),
                ).password("{noop}unused")
                .authorities(emptyList<GrantedAuthority>())
                .build()
        }

    @Bean
    fun relyingPartyOperations(
        userEntities: PublicKeyCredentialUserEntityRepository,
        credentials: UserCredentialRepository,
        publicUrl: PublicUrl,
        passkeys: Passkeys,
        @Value($$"${telex.webauthn.extra-origins:}") extraOrigins: List<String>,
    ): WebAuthnRelyingPartyOperations {
        val rp =
            PublicKeyCredentialRpEntity
                .builder()
                .id(publicUrl.host)
                .name("teleX")
                .build()
        val delegate =
            Webauthn4JRelyingPartyOperations(
                userEntities,
                credentials,
                rp,
                setOf(publicUrl.origin) + extraOrigins.filter { it.isNotBlank() },
            )
        delegate.setCustomizeCreationOptions { options ->
            options.authenticatorSelection(
                AuthenticatorSelectionCriteria
                    .builder()
                    .residentKey(ResidentKeyRequirement.REQUIRED)
                    .build(),
            )
        }
        return TelexRelyingPartyOperations(delegate, passkeys) {
            (
                org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes()
                    as? org.springframework.web.context.request.ServletRequestAttributes
            )?.request
                ?.getHeader(HttpHeaders.USER_AGENT)
        }
    }
}

/** A passkey assertion that verified becomes the one Sign-in Session and its cookie (ADR-0001, AC-104). */
@Component
class PasskeySignInHandlers(
    private val passkeys: Passkeys,
    private val sessions: SignInSessions,
    private val cookies: SessionCookies,
    private val json: JsonMapper,
    private val meters: MeterRegistry,
) {
    val success =
        AuthenticationSuccessHandler { request, response, authentication ->
            val owner = ownerOf(authentication)
            if (owner == null) {
                rejected(response)
            } else {
                val started =
                    sessions.start(
                        owner,
                        cookies.read(request),
                        request.getHeader(HttpHeaders.USER_AGENT),
                        request.getHeader("X-Telex-Time-Zone"),
                        false,
                    )
                cookies.write(response, started.key)
                meters.counter("telex.signin.redeemed", "method", "passkey").increment()
                response.status = HttpStatus.OK.value()
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.outputStream.write(json.writeValueAsBytes(mapOf("createdAccount" to false)))
            }
        }

    val failure = AuthenticationFailureHandler { _, response, _ -> rejected(response) }

    private fun ownerOf(authentication: Authentication): OwnerId? =
        when (val principal = authentication.principal) {
            is UserDetails -> passkeys.ownerOf(principal.username)
            is PublicKeyCredentialUserEntity -> passkeys.ownerOf(principal.name)
            else -> null
        }

    private fun rejected(response: HttpServletResponse) =
        writeProblem(json, response, HttpStatus.UNAUTHORIZED, "passkey-rejected", "That passkey did not work.")
}

/**
 * Registration ceremonies need a live Sign-in Session (401 otherwise), and a registration that does not verify is a
 * 400 `passkey-registration-failed`; anything else (a database failure, say) is a 503 `unavailable`. Never an empty
 * body.
 */
class PasskeyRegistrationFilter(
    private val json: JsonMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.servletPath.startsWith(REGISTER)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || authentication is AnonymousAuthenticationToken) {
            throw InsufficientAuthenticationException("Sign in to continue.")
        }
        val isRegistration = request.method == HttpMethod.POST.name() && request.servletPath == REGISTER
        try {
            chain.doFilter(request, response)
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            if (e is AuthenticationException || e is AccessDeniedException) throw e
            if (e is WebAuthnException || e is IllegalArgumentException) {
                logger.debug("Passkey registration refused", e)
                refuse(response)
            } else {
                logger.error("Passkey registration failed", e)
                response.reset()
                writeProblem(json, response, HttpStatus.SERVICE_UNAVAILABLE, "unavailable", "Try again in a moment.")
            }
            return
        }
        if (isRegistration && response.status == HttpStatus.BAD_REQUEST.value() &&
            !response.isCommitted
        ) {
            refuse(response)
        }
    }

    private fun refuse(response: HttpServletResponse) {
        response.reset()
        writeProblem(
            json,
            response,
            HttpStatus.BAD_REQUEST,
            "passkey-registration-failed",
            "The passkey could not be created.",
        )
    }

    private companion object {
        const val REGISTER = "/webauthn/register"
    }
}

internal fun writeProblem(
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
