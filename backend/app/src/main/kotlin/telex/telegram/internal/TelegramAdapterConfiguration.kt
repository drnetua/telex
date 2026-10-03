package telex.telegram.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram
import telex.telegram.internal.files.SessionDirectories
import telex.telegram.internal.files.SessionDirectoryRetry
import telex.telegram.internal.tdlight.TdlightTelegramSessions
import telex.telegram.tdlib.TdlightFacade
import java.nio.file.Path
import java.time.Clock

/**
 * Chooses the [TelegramSessions] adapter by `telex.telegram.adapter` (`tdlight` default, `fake`).
 * `tdlight` talks to real Telegram through the TDLib facade and is the default.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class TelegramAdapterConfiguration {
    @Bean
    fun sessionDirectories(
        @Value("\${telex.telegram.sessions-dir}") dir: String,
        clock: Clock,
    ) = SessionDirectories(Path.of(dir), clock)

    @Bean
    fun sessionDirectoryRetry(directories: SessionDirectories) = SessionDirectoryRetry(directories)

    @Bean
    @ConditionalOnProperty("telex.telegram.adapter", havingValue = "fake")
    fun fakeTelegram(
        events: ApplicationEventPublisher,
        directories: SessionDirectories,
        @Value("\${telex.telegram.api-id:}") apiId: String,
        @Value("\${telex.telegram.api-hash:}") apiHash: String,
    ): TelegramSessions =
        FakeTelegram(events, configured = apiId.isNotBlank() && apiHash.isNotBlank(), directories = directories)

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty("telex.telegram.adapter", havingValue = "tdlight", matchIfMissing = true)
    fun tdlightTelegram(
        events: ApplicationEventPublisher,
        directories: SessionDirectories,
        @Value("\${telex.telegram.api-id:}") apiId: String,
        @Value("\${telex.telegram.api-hash:}") apiHash: String,
        @Value("\${telex.telegram.use-test-dc:false}") useTestDc: Boolean,
    ): TdlightTelegramSessions =
        TdlightTelegramSessions(
            facade = TdlightFacade(),
            events = events,
            directories = directories,
            apiId = apiId.toIntOrNull(),
            apiHash = apiHash.ifBlank { null },
            useTestDc = useTestDc,
        )
}
