package telex.web.live

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import telex.identity.OwnerId
import telex.identity.SignInSessionId
import telex.identity.SignInSessions
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Open live-update streams per Owner (one instance, ADR-0005). Sends invalidation hints at most once per hint name
 * per [throttle] per Owner (the last one always goes out), a comment heartbeat every [heartbeat], and closes the
 * streams of Sign-in Sessions that have ended.
 */
@Component
class EmitterRegistry(
    private val sessions: SignInSessions,
    @Value($$"${telex.live.heartbeat:PT25S}") private val heartbeat: Duration,
    @Value($$"${telex.live.throttle:PT1S}") private val throttle: Duration,
) {
    private class Stream(
        val ownerId: OwnerId,
        val sessionId: SignInSessionId,
        val emitter: SseEmitter,
    )

    private class Gate {
        var lastSentNanos: Long? = null
        var pending = false
    }

    private val streams = ConcurrentHashMap<OwnerId, CopyOnWriteArraySet<Stream>>()
    private val gates = ConcurrentHashMap<Pair<OwnerId, LiveHint>, Gate>()
    private val scheduler =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "live-updates").apply { isDaemon = true } }

    init {
        scheduler.scheduleWithFixedDelay(
            { runCatching { beat() }.onFailure { log.warn("live-updates heartbeat failed", it) } },
            heartbeat.toMillis(),
            heartbeat.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    /** Opens a stream with no timeout for the Owner's Sign-in Session. */
    fun open(
        ownerId: OwnerId,
        sessionId: SignInSessionId,
    ): SseEmitter {
        val emitter = SseEmitter(0L)
        val stream = Stream(ownerId, sessionId, emitter)
        streams.computeIfAbsent(ownerId) { CopyOnWriteArraySet() }.add(stream)
        emitter.onCompletion { remove(stream) }
        emitter.onTimeout { emitter.complete() }
        emitter.onError { remove(stream) }
        send(stream, ": connected\n\n")
        return emitter
    }

    /** Sends [hint] to every open stream of the Owner, throttled; dropped when the Owner has none open. */
    fun hint(
        ownerId: OwnerId,
        hint: LiveHint,
    ) {
        if (streams[ownerId].isNullOrEmpty()) return
        val gate = gates.computeIfAbsent(ownerId to hint) { Gate() }
        val wait =
            synchronized(gate) {
                val last = gate.lastSentNanos
                val elapsed = if (last == null) Long.MAX_VALUE else System.nanoTime() - last
                when {
                    gate.pending -> {
                        return
                    }

                    elapsed >= throttle.toNanos() -> {
                        gate.lastSentNanos = System.nanoTime()
                        null
                    }

                    else -> {
                        gate.pending = true
                        throttle.toNanos() - elapsed
                    }
                }
            }
        if (wait == null) {
            broadcast(ownerId, hint)
        } else {
            scheduler.schedule({ sendPending(ownerId, hint, gate) }, wait, TimeUnit.NANOSECONDS)
        }
    }

    private fun sendPending(
        ownerId: OwnerId,
        hint: LiveHint,
        gate: Gate,
    ) {
        synchronized(gate) {
            gate.pending = false
            gate.lastSentNanos = System.nanoTime()
        }
        broadcast(ownerId, hint)
    }

    private fun broadcast(
        ownerId: OwnerId,
        hint: LiveHint,
    ) {
        streams[ownerId]?.forEach { send(it, "event: hint\ndata: ${hint.wire}\n\n") }
    }

    private fun beat() {
        streams.values.flatten().forEach { stream ->
            if (sessions.isLive(stream.sessionId)) {
                send(stream, ": keep-alive\n\n")
            } else {
                remove(stream)
                stream.emitter.complete()
            }
        }
    }

    private fun send(
        stream: Stream,
        text: String,
    ) {
        try {
            stream.emitter.send(raw(text))
        } catch (_: IOException) {
            remove(stream)
        } catch (_: IllegalStateException) {
            remove(stream)
        }
    }

    /** The exact bytes [text], with no `data:` prefix added by [SseEmitter]. */
    private fun raw(text: String): SseEmitter.SseEventBuilder =
        object : SseEmitter.SseEventBuilder by SseEmitter.event() {
            override fun build(): Set<ResponseBodyEmitter.DataWithMediaType> =
                setOf(ResponseBodyEmitter.DataWithMediaType(text, MediaType.TEXT_PLAIN))
        }

    private fun remove(stream: Stream) {
        val set = streams[stream.ownerId] ?: return
        set.remove(stream)
        if (set.isEmpty() && streams.remove(stream.ownerId, set)) {
            gates.keys.removeIf { it.first == stream.ownerId }
        }
    }

    @PreDestroy
    fun shutdown() {
        scheduler.shutdownNow()
    }

    private companion object {
        val log = LoggerFactory.getLogger(EmitterRegistry::class.java)
    }
}
