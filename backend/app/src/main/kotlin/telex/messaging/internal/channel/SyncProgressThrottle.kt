package telex.messaging.internal.channel

import telex.identity.OwnerId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountSyncProgressed
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Publishes [LinkedAccountSyncProgressed] at most once per [INTERVAL] per account. A change inside the interval
 * schedules one trailing publish, so the last change is never lost (AC-116, AC-121).
 */
class SyncProgressThrottle(
    private val clock: Clock,
    private val publish: (LinkedAccountSyncProgressed) -> Unit,
    private val schedule: (Duration, Runnable) -> Unit,
) {
    private class State(
        var lastPublished: Instant,
        var trailingScheduled: Boolean = false,
    )

    private val states = HashMap<LinkedAccountId, State>()

    fun changed(
        ownerId: OwnerId,
        accountId: LinkedAccountId,
    ) {
        val event = LinkedAccountSyncProgressed(ownerId, accountId)
        val decision = synchronized(states) { decide(accountId) }
        when (decision) {
            is Decision.PublishNow -> publish(event)
            is Decision.Trailing -> schedule(decision.wait) { trailing(event) }
            Decision.Covered -> Unit
        }
    }

    private sealed interface Decision {
        data object PublishNow : Decision

        data object Covered : Decision

        data class Trailing(
            val wait: Duration,
        ) : Decision
    }

    private fun decide(accountId: LinkedAccountId): Decision {
        val now = clock.instant()
        val state = states[accountId]
        return when {
            state == null || Duration.between(state.lastPublished, now) >= INTERVAL -> {
                states[accountId] = State(now)
                Decision.PublishNow
            }

            state.trailingScheduled -> {
                Decision.Covered
            }

            else -> {
                state.trailingScheduled = true
                Decision.Trailing(INTERVAL.minus(Duration.between(state.lastPublished, now)))
            }
        }
    }

    private fun trailing(event: LinkedAccountSyncProgressed) {
        synchronized(states) {
            states[event.linkedAccountId]?.let {
                it.lastPublished = clock.instant()
                it.trailingScheduled = false
            }
        }
        publish(event)
    }

    companion object {
        val INTERVAL: Duration = Duration.ofSeconds(1)
    }
}
