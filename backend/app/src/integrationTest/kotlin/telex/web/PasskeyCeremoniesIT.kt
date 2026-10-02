package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import telex.TestcontainersConfiguration
import telex.identity.FixedClockConfiguration
import telex.identity.MutableClock
import telex.identity.OwnerId
import telex.identity.SignInSessions
import telex.identity.internal.owner.Owners
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/** AC-89, AC-105, AC-92, AC-104: passkey ceremonies wired to Spring Security WebAuthn and the one session. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PasskeyCeremoniesIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var userEntities: JdbcPublicKeyCredentialUserEntityRepository

    @Autowired lateinit var credentials: JdbcUserCredentialRepository

    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM user_credentials")
        jdbc.execute("DELETE FROM user_entities")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun post(
        path: String,
        key: String?,
        body: String = "{}",
    ): HttpResponse<String> {
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        val r =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .header("Cookie", cookies)
                .header("X-XSRF-TOKEN", "csrf")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return http.send(r, HttpResponse.BodyHandlers.ofString())
    }

    private fun signedIn(): Pair<OwnerId, String> {
        val owner = owners.findOrCreate("anton@mail.com", "anton@mail.com", clock.instant()).first
        return owner to sessions.start(owner, null, "Safari iPhone", "Europe/Kyiv", false).key
    }

    @Test
    fun `AC-89 registration options name the owner as the user and require a discoverable credential`() {
        val (owner, key) = signedIn()

        val r = post("/webauthn/register/options", key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body())
            .contains("\"name\":\"${owner.value}\"", "\"displayName\":\"anton@mail.com\"", "\"id\":\"localhost\"")
            .contains("\"residentKey\":\"required\"")
    }

    @Test
    fun `registration options while signed out are 401 unauthenticated`() {
        val r = post("/webauthn/register/options", null)

        assertThat(r.statusCode()).isEqualTo(401)
        assertThat(r.body()).contains("\"code\":\"unauthenticated\"")
    }

    @Test
    fun `AC-105 a registration that does not verify is 400 passkey-registration-failed and adds no passkey`() {
        val (_, key) = signedIn()
        post("/webauthn/register/options", key)

        val r = post("/webauthn/register", key, """{"publicKey":{"credential":{},"label":"x"}}""")

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"passkey-registration-failed\"")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_credentials", Int::class.java)).isZero()
    }

    @Test
    fun `AC-92 authentication options are public with no allowed credentials`() {
        val r = post("/webauthn/authenticate/options", null)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"allowCredentials\":[]")
    }

    @Test
    fun `AC-92 AC-104 an assertion for an unknown credential is 401 passkey-rejected and starts no session`() {
        val r = post("/login/webauthn", null, """{"id":"AAAA","rawId":"AAAA","type":"public-key","response":{}}""")

        assertThat(r.statusCode()).isEqualTo(401)
        assertThat(r.body()).contains("\"code\":\"passkey-rejected\"")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sign_in_session", Int::class.java)).isZero()
    }

    @Test
    fun `created and last-used survive a save and reload through timestamptz unchanged`() {
        val (owner, _) = signedIn()
        val entity =
            ImmutablePublicKeyCredentialUserEntity
                .builder()
                .id(Bytes.random())
                .name(owner.value.toString())
                .displayName("anton@mail.com")
                .build()
        userEntities.save(entity)
        val created = Instant.parse("2026-10-02T14:00:00Z")

        credentials.save(
            org.springframework.security.web.webauthn.api.TestCredentialRecords
                .userCredential()
                .userEntityUserId(entity.id)
                .created(created)
                .lastUsed(null)
                .build(),
        )

        val reloaded = credentials.findByUserId(entity.id).single()
        assertThat(reloaded.created).isEqualTo(created)
        assertThat(reloaded.lastUsed).isNull()
    }
}
