package telex.messaging.internal.lifecycle

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import telex.messaging.LinkedAccountState
import telex.telegram.SessionState
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramSessionStateChanged
import telex.telegram.TelegramSessions
import java.util.concurrent.ConcurrentHashMap

/**
 * Follows Telegram's session state (AC-117, AC-122): Ready is Connected, Connecting is Reconnecting, and only Closed,
 * which Telegram confirms, is Session lost. An event of an unknown session is dropped (ADR-0002), as is one that
 * is not newer than the last seen of its session.
 */
@Component
class SessionStateListener(
    private val states: LinkedAccountStates,
    private val telegram: TelegramSessions,
    private val metrics: LifecycleMetrics,
) {
    private val lastSequence = ConcurrentHashMap<TelegramSessionId, Long>()

    /** A reopened session starts counting its sequence again. */
    fun forget(id: TelegramSessionId) {
        lastSequence.remove(id)
    }

    @EventListener
    fun on(event: TelegramSessionStateChanged) {
        var newer = false
        lastSequence.compute(event.sessionId) { _, last ->
            if (last == null || event.sequence > last) {
                newer = true
                event.sequence
            } else {
                last
            }
        }
        if (!newer) return
        val target =
            when (event.state) {
                SessionState.Ready -> LinkedAccountState.CONNECTED
                SessionState.Connecting -> LinkedAccountState.RECONNECTING
                SessionState.Closed -> LinkedAccountState.SESSION_LOST
            }
        states.transition(event.sessionId, target)
        if (event.state != SessionState.Connecting) metrics.reconnectSettled(event.sessionId)
        if (event.state == SessionState.Closed) {
            lastSequence.remove(event.sessionId)
            telegram.close(event.sessionId)
        }
    }
}
