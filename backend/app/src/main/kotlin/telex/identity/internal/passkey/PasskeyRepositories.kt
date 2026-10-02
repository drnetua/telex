package telex.identity.internal.passkey

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository

/** The framework's JDBC stores over `user_entities` and `user_credentials` (ADR-0002). */
@Configuration(proxyBeanMethods = false)
class PasskeyRepositories {
    @Bean
    fun publicKeyCredentialUserEntityRepository(jdbc: JdbcOperations) =
        JdbcPublicKeyCredentialUserEntityRepository(jdbc)

    /** Reads a never-used Passkey (`last_used` NULL) instead of failing on it. */
    @Bean
    fun userCredentialRepository(jdbc: JdbcOperations) =
        JdbcUserCredentialRepository(NeverUsedTolerantJdbcOperations(jdbc))
}
