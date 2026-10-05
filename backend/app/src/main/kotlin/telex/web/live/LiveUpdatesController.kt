package telex.web.live

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import telex.identity.SignedInOwner

/** The live-update stream (ADR-0005): invalidation hints only, never Owner data. */
@RestController
@RequestMapping("/api/v1/live-updates")
class LiveUpdatesController(
    private val registry: EmitterRegistry,
) {
    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun open(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): ResponseEntity<SseEmitter> =
        ResponseEntity
            .ok()
            .header("Cache-Control", "no-store")
            .header("X-Accel-Buffering", "no")
            .body(registry.open(principal.ownerId, principal.sessionId))
}
