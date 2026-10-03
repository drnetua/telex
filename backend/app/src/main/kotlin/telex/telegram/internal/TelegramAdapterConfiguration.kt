package telex.telegram.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import telex.telegram.TelegramSessions
import telex.telegram.internal.fake.FakeTelegram

/**
 * Chooses the [TelegramSessions] adapter by `telex.telegram.adapter` (`tdlight` default, `fake`).
 * The `tdlight` adapter arrives with the E02 spike; until then only `fake` provides the bean.
 */
@Configuration(proxyBeanMethods = false)
class TelegramAdapterConfiguration {
    @Bean
    @ConditionalOnProperty("telex.telegram.adapter", havingValue = "fake")
    fun fakeTelegram(
        events: ApplicationEventPublisher,
        @Value("\${telex.telegram.api-id:}") apiId: String,
        @Value("\${telex.telegram.api-hash:}") apiHash: String,
    ): TelegramSessions = FakeTelegram(events, configured = apiId.isNotBlank() && apiHash.isNotBlank())
}
