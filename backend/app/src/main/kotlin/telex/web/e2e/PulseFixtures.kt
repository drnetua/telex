package telex.web.e2e

import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import telex.identity.SignedInOwner
import telex.inbox.InboxSource
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import telex.shared.StatusConditionSource
import telex.web.api.Pulse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory, per-Owner pulse values for e2e tests; stands in for real producers. Exists only under `e2e`. */
@Profile("e2e")
@Component
class PulseFixtures :
    InboxSource,
    StatusConditionSource {
    private val counts = ConcurrentHashMap<UUID, Int>()
    private val conditions = ConcurrentHashMap<UUID, Set<String>>()

    fun set(
        ownerId: UUID,
        inboxCount: Int,
        codes: Collection<String>,
    ) {
        counts[ownerId] = inboxCount
        conditions[ownerId] = codes.toSet()
    }

    override fun countWaiting(ownerId: UUID): Int = counts[ownerId] ?: 0

    override fun activeConditions(ownerId: UUID): Set<String> = conditions[ownerId] ?: emptySet()
}

/** A kebab-case label of at most 63 characters, as `StatusConditionCode` in the contract. */
private val conditionCode = Regex("^(?=.{1,63}$)[a-z0-9]([-a-z0-9]*[a-z0-9])?$")

/** Sets the calling Owner's fixture values; it cannot name another Owner. */
@Profile("e2e")
@RestController
@RequestMapping("/api/v1/e2e-fixtures/pulse")
class PulseFixtureController(
    private val fixtures: PulseFixtures,
) {
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun set(
        @AuthenticationPrincipal principal: SignedInOwner,
        @RequestBody @Valid body: Pulse,
    ) {
        if (body.conditions.orEmpty().any { !conditionCode.matches(it) }) {
            val message = "Condition codes are kebab-case labels."
            throw DomainProblem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                message,
                listOf(FieldProblem("conditions", "pattern", message)),
            )
        }
        fixtures.set(principal.ownerId.value, body.inboxCount ?: 0, body.conditions.orEmpty())
    }
}
