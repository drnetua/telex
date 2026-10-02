package telex.web.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignIn
import telex.identity.SignInGrantId
import telex.identity.SignInSessions
import telex.identity.SignedIn
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import telex.web.security.SessionCookies
import java.util.UUID

data class RequestSignInEmail(
    @field:NotBlank @field:Size(max = 254) val email: String?,
)

data class LinkTokenBody(
    @field:NotBlank @field:Size(min = 1, max = 128) val linkToken: String?,
)

data class CodeBody(
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{6}$") val code: String?,
)

data class GrantCreated(
    val grantId: UUID,
    val email: String,
)

data class LinkPreviewed(
    val email: String,
)

data class SignedInBody(
    val createdAccount: Boolean,
)

/** The email sign-in endpoints: request, preview and redeem a link, redeem a code, sign out. */
@RestController
@RequestMapping("/api/v1")
class SignInController(
    private val signIn: SignIn,
    private val sessions: SignInSessions,
    private val cookies: SessionCookies,
) {
    @PostMapping("/sign-in/grants")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestEmail(
        @Valid @RequestBody body: RequestSignInEmail,
    ): GrantCreated {
        val issued = signIn.request(body.email.orEmpty())
        return GrantCreated(issued.grantId.value, issued.emailAsTyped)
    }

    @PostMapping("/sign-in/link/preview")
    fun previewLink(
        @Valid @RequestBody body: LinkTokenBody,
    ) = LinkPreviewed(signIn.preview(body.linkToken.orEmpty()).email)

    @PostMapping("/sign-in/link/redeem")
    fun redeemLink(
        @Valid @RequestBody body: LinkTokenBody,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestHeader("User-Agent", required = false) userAgent: String?,
        @RequestHeader("X-Telex-Time-Zone", required = false) timeZone: String?,
    ): SignedInBody =
        done(
            response,
            signIn.redeemByLink(body.linkToken.orEmpty(), cookies.read(request), userAgent, timeZone),
        )

    @PostMapping("/sign-in/grants/{grantId}/code")
    fun redeemCode(
        @PathVariable grantId: String,
        @Valid @RequestBody body: CodeBody,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestHeader("User-Agent", required = false) userAgent: String?,
        @RequestHeader("X-Telex-Time-Zone", required = false) timeZone: String?,
    ): SignedInBody =
        done(
            response,
            signIn.redeemByCode(parse(grantId), body.code.orEmpty(), cookies.read(request), userAgent, timeZone),
        )

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOut(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        sessions.endByKey(cookies.read(request))
        cookies.clear(response)
    }

    private fun done(
        response: HttpServletResponse,
        signedIn: SignedIn,
    ): SignedInBody {
        cookies.write(response, signedIn.key)
        return SignedInBody(signedIn.createdAccount)
    }

    private fun parse(grantId: String): SignInGrantId =
        try {
            SignInGrantId(UUID.fromString(grantId))
        } catch (e: IllegalArgumentException) {
            throw DomainProblem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Some fields are invalid.",
                listOf(FieldProblem("grantId", "invalid", "Not a valid grant id.")),
                e,
            )
        }
}
