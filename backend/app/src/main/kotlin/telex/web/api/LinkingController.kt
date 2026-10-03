package telex.web.api

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignedInOwner
import telex.messaging.LinkedAccountId
import telex.messaging.Linking
import telex.messaging.LinkingAttemptView
import telex.messaging.LinkingOrigin
import telex.messaging.LinkingOutcome
import telex.messaging.LinkingProgress
import java.util.Locale
import java.util.UUID

data class StartLinkingRequest(
    @field:NotNull @field:Pattern(regexp = "^(inbox|accounts)$") val origin: String?,
    val targetLinkedAccountId: UUID? = null,
)

/** Handed to Telegram as typed; never stored, echoed or logged, so [toString] hides it. */
data class LinkingPhoneRequest(
    @field:NotEmpty @field:Size(max = PHONE_MAX) val phoneNumber: String?,
) {
    override fun toString() = "LinkingPhoneRequest"
}

data class LinkingCodeRequest(
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{1,16}$") val code: String?,
) {
    override fun toString() = "LinkingCodeRequest"
}

data class LinkingPasswordRequest(
    @field:NotEmpty @field:Size(max = PASSWORD_MAX) val password: String?,
) {
    override fun toString() = "LinkingPasswordRequest"
}

data class LinkingAttemptBody(
    val step: String,
    val origin: String,
    val targetLinkedAccountId: UUID?,
    val codeLength: Int?,
    val passwordHint: String?,
)

/** `outcome` is `next` (with [attempt]) or `linked` / `signed-in-again` (with [linkedAccountId] and [origin]). */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LinkingStepBody(
    val outcome: String,
    val attempt: LinkingAttemptBody? = null,
    val linkedAccountId: UUID? = null,
    val origin: String? = null,
)

/** The Owner's one linking attempt as a singleton resource: no attempt id ever reaches the browser. */
@RestController
@RequestMapping("/api/v1/linking-attempt")
class LinkingController(
    private val linking: Linking,
) {
    @GetMapping
    fun get(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = linking.get(principal.ownerId).body()

    @PostMapping
    fun start(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody request: StartLinkingRequest,
    ): ResponseEntity<LinkingAttemptBody> {
        val started =
            linking.start(
                principal.ownerId,
                principal.sessionId,
                LinkingOrigin.valueOf(checkNotNull(request.origin).uppercase(Locale.ROOT)),
                request.targetLinkedAccountId?.let(::LinkedAccountId),
            )
        val status = if (started.resumed) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(started.attempt.body())
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = linking.cancel(principal.ownerId)

    @PostMapping("/phone")
    fun phone(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody request: LinkingPhoneRequest,
    ) = attempt(linking.submitPhone(principal.ownerId, principal.sessionId, checkNotNull(request.phoneNumber)))

    @PostMapping("/code/resend")
    fun resend(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = attempt(linking.resendCode(principal.ownerId, principal.sessionId))

    @PostMapping("/code")
    fun code(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody request: LinkingCodeRequest,
    ) = result(linking.submitCode(principal.ownerId, principal.sessionId, checkNotNull(request.code)))

    @PostMapping("/password")
    fun password(
        @AuthenticationPrincipal principal: SignedInOwner,
        @Valid @RequestBody request: LinkingPasswordRequest,
    ) = result(linking.submitPassword(principal.ownerId, principal.sessionId, checkNotNull(request.password)))

    /** The phone and resend steps always end at an attempt step (the code step), never at a completion. */
    private fun attempt(progress: LinkingProgress): LinkingAttemptBody =
        when (progress) {
            is LinkingProgress.Step -> progress.attempt.body()
            is LinkingProgress.Completed -> error("A phone step cannot complete the attempt")
        }

    private fun result(progress: LinkingProgress): LinkingStepBody =
        when (progress) {
            is LinkingProgress.Step -> {
                LinkingStepBody("next", attempt = progress.attempt.body())
            }

            is LinkingProgress.Completed -> {
                val outcome =
                    when (progress.outcome) {
                        LinkingOutcome.LINKED -> "linked"
                        LinkingOutcome.SIGNED_IN_AGAIN -> "signed-in-again"
                    }
                LinkingStepBody(
                    outcome,
                    linkedAccountId = progress.linkedAccountId.value,
                    origin = progress.origin.wire(),
                )
            }
        }

    private fun LinkingAttemptView.body() =
        LinkingAttemptBody(step.wire(), origin.wire(), targetLinkedAccountId?.value, codeLength, passwordHint)

    private fun Enum<*>.wire() = name.lowercase(Locale.ROOT)
}

private const val PHONE_MAX = 32
private const val PASSWORD_MAX = 1024
