package telex.messaging.internal.channel

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import telex.messaging.LinkedAccountId
import telex.messaging.internal.account.LinkedAccountRows
import telex.telegram.TelegramChatsChanged
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Keeps the `channel` rows of a Linked Account equal to its Telegram chat list (AC-116, AC-121). Synchronous
 * `@EventListener` on purpose: Telegram data (titles, chat ids) must never enter `event_publication`. Only the
 * id-only [telex.messaging.LinkedAccountSyncProgressed] leaves, throttled.
 */
@Component
class ChatListListener(
    private val accounts: LinkedAccountRows,
    private val channels: ChannelRows,
    private val tx: TransactionTemplate,
    private val throttle: SyncProgressThrottle,
    private val clock: Clock,
    private val meters: MeterRegistry,
) {
    private val started = ConcurrentHashMap<LinkedAccountId, Instant>()

    @EventListener
    fun on(event: TelegramChatsChanged) {
        val account = accounts.findBySession(event.sessionId) ?: return
        val now = clock.instant()
        started.putIfAbsent(account.id, now)
        val firstCompletion =
            tx.execute {
                channels.upsert(account.ownerId.value, account.id, event.upserted)
                channels.delete(account.id, event.removedChatIds)
                event.loadedChatIds?.let { channels.deleteNotIn(account.id, it) }
                channels.recordProgress(account.id, event.total, event.loadCompleted, now)
            } == true
        if (firstCompletion) {
            started.remove(account.id)?.let {
                Timer
                    .builder("telex.chat_sync.duration")
                    .register(meters)
                    .record(Duration.between(it, clock.instant()))
            }
        } else if (event.loadCompleted) {
            started.remove(account.id)
        }
        throttle.changed(account.ownerId, account.id)
    }
}

@Configuration(proxyBeanMethods = false)
class ChatSyncConfiguration {
    @Bean
    fun syncProgressThrottle(
        clock: Clock,
        events: ApplicationEventPublisher,
        tx: TransactionTemplate,
    ): SyncProgressThrottle {
        val scheduler =
            Executors.newSingleThreadScheduledExecutor { Thread(it, "chat-sync-progress").apply { isDaemon = true } }
        return SyncProgressThrottle(clock, {
            tx.executeWithoutResult { _ ->
                events.publishEvent(it)
            }
        }) { delay, task ->
            scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}
