package telex.messaging.internal.lifecycle

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import telex.messaging.LinkedAccountState
import telex.messaging.internal.account.LinkedAccountRows
import telex.telegram.TelegramSessionId
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** `telex.telegram.sessions.active{state}` and `telex.linked_accounts.reconnect.duration`. */
@Component
class LifecycleMetrics(
    rows: LinkedAccountRows,
    private val meters: MeterRegistry,
) {
    private val reconnectStarted = ConcurrentHashMap<TelegramSessionId, Long>()

    init {
        LinkedAccountState.entries.forEach { state ->
            Gauge
                .builder("telex.telegram.sessions.active") { rows.countByState(state).toDouble() }
                .tag("state", state.wire)
                .strongReference(true)
                .register(meters)
        }
    }

    fun reconnectStarted(id: TelegramSessionId) {
        reconnectStarted[id] = System.nanoTime()
    }

    /** The session answered after a reopen, as connected or ended. */
    fun reconnectSettled(id: TelegramSessionId) {
        val started = reconnectStarted.remove(id) ?: return
        Timer
            .builder("telex.linked_accounts.reconnect.duration")
            .register(meters)
            .record(Duration.ofNanos(System.nanoTime() - started))
    }
}
