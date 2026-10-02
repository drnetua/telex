package telex.web.api

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import telex.identity.SignedInOwner
import telex.shared.DomainProblem
import java.time.Instant
import java.util.UUID

data class SessionItem(
    val id: UUID,
    val userAgentLabel: String,
    val deviceType: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val current: Boolean,
)

data class SessionList(
    val items: List<SessionItem>,
)

/** The signed-in Owner's own Sign-in Sessions; another Owner's session looks missing (AC-97). */
@RestController
@RequestMapping("/api/v1/sessions")
class SessionsController(
    private val sessions: SignInSessions,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = SessionList(
        sessions.listMine(principal.ownerId, principal.sessionId).map {
            SessionItem(it.id.value, it.userAgentLabel, it.deviceType, it.startedAt, it.lastActivityAt, it.current)
        },
    )

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun end(
        @AuthenticationPrincipal principal: SignedInOwner,
        @PathVariable sessionId: String,
    ) {
        val id =
            runCatching { SignInSessionId(UUID.fromString(sessionId)) }.getOrNull()
        if (id == null || !sessions.endMine(principal.ownerId, id)) {
            throw DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found.")
        }
    }

    @PostMapping("/end-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun endOthers(
        @AuthenticationPrincipal principal: SignedInOwner,
    ) = sessions.endMyOthers(principal.ownerId, principal.sessionId)
}
