package telex.web.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignedInOwner
import telex.inbox.Inbox
import telex.shared.StatusConditionSource

/** What the shell polls: the Inbox count and the active Status Banner condition codes of one Owner. */
data class Pulse(
    @field:NotNull @field:Min(0) val inboxCount: Int?,
    @field:NotNull val conditions: List<String>?,
)

/** The one live channel (ADR-0002): answers for the calling Owner only and is not logged per request. */
@RestController
@RequestMapping("/api/v1/pulse")
class PulseController(
    private val inbox: Inbox,
    private val conditionSources: List<StatusConditionSource>,
) {
    @GetMapping
    fun pulse(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): Pulse {
        val owner = principal.ownerId.value
        val conditions = conditionSources.flatMapTo(linkedSetOf()) { it.activeConditions(owner) }
        return Pulse(inbox.countWaiting(owner), conditions.toList())
    }
}
