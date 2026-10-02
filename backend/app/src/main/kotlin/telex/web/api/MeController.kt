package telex.web.api

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import telex.identity.OwnerProfiles
import telex.identity.SignedInOwner
import telex.shared.DomainProblem
import java.util.UUID

data class MeBody(
    val ownerId: UUID,
    val email: String,
    val linkedAccountCount: Int,
)

@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val profiles: OwnerProfiles,
) {
    @GetMapping
    fun me(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): MeBody {
        val me =
            profiles.me(principal.ownerId)
                ?: throw DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found.")
        return MeBody(me.ownerId.value, me.email, me.linkedAccountCount)
    }
}
