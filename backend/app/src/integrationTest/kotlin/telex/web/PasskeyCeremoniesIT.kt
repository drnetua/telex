package telex.web

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import telex.identity.Passkeys
import telex.identity.SessionResolution
import telex.identity.SignInSessions
import telex.identity.TestCredentialRecords
import telex.identity.internal.owner.Owners
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/** AC-89, AC-105, AC-92, AC-104: passkey ceremonies wired to Spring Security WebAuthn and the one session. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
class PasskeyCeremoniesIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var passkeys: Passkeys

    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var userEntities: JdbcPublicKeyCredentialUserEntityRepository

    @Autowired lateinit var credentials: JdbcUserCredentialRepository

    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        mailer.reset()
        jdbc.execute("DELETE FROM event_publication")
        jdbc.execute("DELETE FROM user_credentials")
        jdbc.execute("DELETE FROM user_entities")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun post(
        path: String,
        key: String?,
        body: String = "",
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
        val response = http.send(r, HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(
            "POST",
            path,
            body,
            mapOf("Content-Type" to "application/json", "X-XSRF-TOKEN" to "csrf", "Cookie" to cookies),
            response,
        )
        return response
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
    fun `AC-89 a 254-character email still gets registration options`() {
        val email = "x".repeat(MAX_EMAIL - "@mail.com".length) + "@mail.com"
        val owner = owners.findOrCreate(email, email, clock.instant()).first
        val key = sessions.start(owner, null, "Safari iPhone", "Europe/Kyiv", false).key

        val r = post("/webauthn/register/options", key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"displayName\":\"$email\"")
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

    private class Registered(
        val owner: OwnerId,
        val heldKey: String,
        val device: SoftwareAuthenticator,
        val credentialId: String,
        val userHandle: Bytes,
    )

    /** An Owner already signed in on this browser, holding a real passkey for a software authenticator. */
    private fun ownerWithPasskey(): Registered {
        val (owner, heldKey) = signedIn()
        passkeys.ensureUserEntity(owner)
        val entity = userEntities.findByUsername(owner.value.toString())!!
        val device = SoftwareAuthenticator()
        credentials.save(
            TestCredentialRecords
                .userCredential()
                .userEntityUserId(entity.id)
                .credentialId(device.credentialId)
                .publicKey(device.publicKeyCose)
                .attestationObject(device.attestationObject)
                .created(clock.instant())
                .lastUsed(null)
                .build(),
        )
        // The setup's own session start emails a notice; let it finish so the test sees only the passkey sign-in's.
        await().atMost(Duration.ofSeconds(5)).until {
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE completion_date IS NULL",
                Int::class.java,
            ) ==
                0
        }
        mailer.reset()
        return Registered(owner, heldKey, device, device.credentialId.toBase64UrlString(), entity.id)
    }

    /** Runs the browser's passkey sign-in: fetch options (keeping the HTTP session), sign the challenge, post it. */
    private fun passkeySignIn(who: Registered): HttpResponse<String> {
        val options = post("/webauthn/authenticate/options", who.heldKey)
        val httpSession =
            options
                .headers()
                .allValues("Set-Cookie")
                .first { it.startsWith("JSESSIONID=") }
                .substringBefore(";")
        val challenge = Regex("\"challenge\":\"([^\"]+)\"").find(options.body())!!.groupValues[1]
        val body = who.device.assertion(challenge, who.userHandle)
        val cookies = "XSRF-TOKEN=csrf; telex_session=${who.heldKey}; $httpSession"
        val r =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/login/webauthn"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/130.0")
                .header("Cookie", cookies)
                .header("X-XSRF-TOKEN", "csrf")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = http.send(r, HttpResponse.BodyHandlers.ofString())
        ContractValidator.assertConforms(
            "POST",
            "/login/webauthn",
            body,
            mapOf("Content-Type" to "application/json", "X-XSRF-TOKEN" to "csrf", "Cookie" to cookies),
            response,
        )
        return response
    }

    private fun liveSessions() =
        jdbc.queryForObject("SELECT count(*) FROM sign_in_session WHERE ended_at IS NULL", Int::class.java)

    @Test
    fun `AC-92 AC-104 AC-98 a passkey sign-in ends the held session, starts one for the Owner and sends the notice`() {
        val who = ownerWithPasskey()

        val r = passkeySignIn(who)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(r.body()).contains("\"createdAccount\":false")
        assertThat(sessions.resolve(who.heldKey, background = true)).isEqualTo(SessionResolution.Ended)
        val newKey =
            r
                .headers()
                .allValues("Set-Cookie")
                .first { it.startsWith("telex_session=") }
                .substringAfter("=")
                .substringBefore(";")
        val live = sessions.resolve(newKey, background = true)
        assertThat(live).isInstanceOf(SessionResolution.Live::class.java)
        assertThat((live as SessionResolution.Live).ownerId).isEqualTo(who.owner)
        assertThat(liveSessions()).isEqualTo(1)
        await().atMost(Duration.ofSeconds(5)).until { mailer.sent.any { it.template == "new-sign-in" } }
        assertThat(mailer.sent.single { it.template == "new-sign-in" }.subject).isEqualTo("New sign-in to teleX")
    }

    @Test
    fun `AC-92 an assertion for a passkey that was removed is refused and starts no session`() {
        val who = ownerWithPasskey()
        assertThat(passkeys.removeMine(who.owner, who.credentialId)).isTrue()
        val before = liveSessions()

        val r = passkeySignIn(who)

        assertThat(r.statusCode()).isEqualTo(401)
        assertThat(r.body()).contains("\"code\":\"passkey-rejected\"")
        assertThat(liveSessions()).isEqualTo(before)
        assertThat(sessions.resolve(who.heldKey, background = true)).isInstanceOf(SessionResolution.Live::class.java)
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
            TestCredentialRecords
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

    private companion object {
        const val MAX_EMAIL = 254
    }
}
