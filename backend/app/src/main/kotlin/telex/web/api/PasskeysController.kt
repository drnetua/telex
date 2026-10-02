package telex.web.api

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import telex.identity.MyPasskey
import telex.identity.Passkeys
import telex.identity.SignedInOwner
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import java.time.Instant

data class PasskeyItem(
    val id: String,
    val label: String,
    val createdAt: Instant?,
    val lastUsedAt: Instant?,
)

data class PasskeyList(
    val items: List<PasskeyItem>,
)

/** The signed-in Owner's own Passkeys; another Owner's passkey looks missing (AC-97). */
@RestController
@RequestMapping("/api/v1/passkeys")
class PasskeysController(
    private val passkeys: Passkeys,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = PasskeyList(passkeys.listMine(principal.ownerId).map(MyPasskey::toItem))

    @DeleteMapping("/{passkeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable passkeyId: String,
    ) {
        if (!ID_FORMAT.matches(passkeyId) || passkeyId.length > MAX_ID_LENGTH) {
            throw DomainProblem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Some fields are invalid.",
                listOf(FieldProblem("passkeyId", "invalid", "Not a passkey id.")),
            )
        }
        if (!passkeys.removeMine(principal.ownerId, passkeyId)) {
            throw DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found.")
        }
    }

    private companion object {
        val ID_FORMAT = Regex("^[A-Za-z0-9_-]+$")
        const val MAX_ID_LENGTH = 1000
    }
}

private fun MyPasskey.toItem() = PasskeyItem(id, label, createdAt, lastUsedAt)
