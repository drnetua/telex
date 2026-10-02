package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import telex.TestcontainersConfiguration
import telex.identity.internal.owner.Owners
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/** AC-89: the Owner's WebAuthn user entity and a just-registered passkey are written safely. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PasskeysIT {
    @Autowired lateinit var passkeys: Passkeys

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var userEntities: JdbcPublicKeyCredentialUserEntityRepository

    @Autowired lateinit var credentials: JdbcUserCredentialRepository

    @BeforeEach
    fun reset() {
        jdbc.execute("DELETE FROM user_credentials")
        jdbc.execute("DELETE FROM user_entities")
        jdbc.execute("DELETE FROM owner")
    }

    private fun owner(): OwnerId = owners.findOrCreate("anton@mail.com", "anton@mail.com", Instant.now()).first

    private fun count(sql: String): Int? = jdbc.queryForObject(sql, Int::class.java)

    @Test
    fun `AC-89 concurrent first registrations for one Owner all succeed and leave one user entity`() {
        val owner = owner()
        val callers = 8
        val barrier = CyclicBarrier(callers)
        val pool = Executors.newFixedThreadPool(callers)
        try {
            val results =
                (1..callers).map {
                    pool.submit {
                        barrier.await()
                        passkeys.ensureUserEntity(owner)
                    }
                }
            results.forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }

        assertThat(count("SELECT count(*) FROM user_entities")).isEqualTo(1)
    }

    @Test
    fun `AC-89 a registered passkey is stored once with no last-used date`() {
        val owner = owner()
        passkeys.ensureUserEntity(owner)
        val entity = userEntities.findByUsername(owner.value.toString())!!

        passkeys.register {
            // The framework's own save stamps last_used with now.
            TestCredentialRecords
                .userCredential()
                .userEntityUserId(entity.id)
                .lastUsed(Instant.now())
                .build()
                .also(credentials::save)
        }

        assertThat(count("SELECT count(*) FROM user_credentials")).isEqualTo(1)
        assertThat(count("SELECT count(*) FROM user_credentials WHERE last_used IS NULL")).isEqualTo(1)
    }

    @Test
    fun `AC-89 a registration that fails after the framework saved the credential leaves no passkey`() {
        val owner = owner()
        passkeys.ensureUserEntity(owner)
        val entity = userEntities.findByUsername(owner.value.toString())!!

        runCatching {
            passkeys.register {
                TestCredentialRecords
                    .userCredential()
                    .userEntityUserId(entity.id)
                    .lastUsed(Instant.now())
                    .build()
                    .also(credentials::save)
                error("boom")
            }
        }

        assertThat(count("SELECT count(*) FROM user_credentials")).isZero()
    }
}
