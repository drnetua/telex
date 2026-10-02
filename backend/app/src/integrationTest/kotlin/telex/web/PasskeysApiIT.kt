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
import telex.identity.TestCredentialRecords
import telex.identity.internal.owner.Owners
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

/** AC-89, AC-91, AC-92, AC-97: list and remove my Passkeys, filtered by my WebAuthn user entity. */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class)
class PasskeysApiIT(
    @LocalServerPort private val port: Int,
) {
    @Autowired lateinit var sessions: SignInSessions

    @Autowired lateinit var owners: Owners

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    @Autowired lateinit var userEntities: JdbcPublicKeyCredentialUserEntityRepository

    @Autowired lateinit var credentials: JdbcUserCredentialRepository

    private val http = HttpClient.newHttpClient()

    private class Who(
        val owner: OwnerId,
        val key: String,
        val entityId: Bytes,
    )

    @BeforeEach
    fun reset() {
        clock.set(Instant.parse("2026-10-02T14:00:00Z"))
        jdbc.execute("DELETE FROM user_credentials")
        jdbc.execute("DELETE FROM user_entities")
        jdbc.execute("DELETE FROM sign_in_session")
        jdbc.execute("DELETE FROM owner")
    }

    private fun who(
        email: String,
        withEntity: Boolean = true,
    ): Who {
        val owner = owners.findOrCreate(email, email, clock.instant()).first
        val entityId = Bytes.random()
        if (withEntity) {
            userEntities.save(
                ImmutablePublicKeyCredentialUserEntity
                    .builder()
                    .id(entityId)
                    .name(owner.value.toString())
                    .displayName(email)
                    .build(),
            )
        }
        val key = sessions.start(owner, null, "Safari iPhone", "Europe/Kyiv", false).key
        return Who(owner, key, entityId)
    }

    private fun passkey(
        who: Who,
        created: Instant,
        lastUsed: Instant? = null,
    ): String {
        val record =
            TestCredentialRecords
                .userCredential()
                .userEntityUserId(who.entityId)
                .created(created)
                .lastUsed(lastUsed)
                .build()
        credentials.save(record)
        return record.credentialId.toBase64UrlString()
    }

    private fun call(
        method: String,
        path: String,
        key: String?,
    ): HttpResponse<String> {
        val cookies = listOfNotNull("XSRF-TOKEN=csrf", key?.let { "telex_session=$it" }).joinToString("; ")
        val r =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .header("Cookie", cookies)
                .header("X-XSRF-TOKEN", "csrf")
                .header("X-Telex-Background", "1")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build()
        val response = http.send(r, HttpResponse.BodyHandlers.ofString())
        val headers = mapOf("X-XSRF-TOKEN" to "csrf", "X-Telex-Background" to "1")
        ContractValidator.assertConforms(method, path, null, headers, response)
        return response
    }

    private fun ids(r: HttpResponse<String>) =
        Regex("\"id\":\"([^\"]+)\"").findAll(r.body()).map { it.groupValues[1] }.toList()

    @Test
    fun `AC-89 list shows name, creation date, last-used date or null, newest first`() {
        val a = who("anton@mail.com")
        val older = passkey(a, Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-20T10:00:00Z"))
        val newer = passkey(a, Instant.parse("2026-10-01T10:00:00Z"))

        val r = call("GET", "/api/v1/passkeys", a.key)

        assertThat(r.statusCode()).isEqualTo(200)
        assertThat(ids(r)).containsExactly(newer, older)
        assertThat(r.body())
            .contains("\"label\":\"Safari on iPhone\"", "\"createdAt\"", "\"lastUsedAt\":null")
            .contains("\"lastUsedAt\":\"2026-09-20T10:00:00Z\"")
    }

    @Test
    fun `AC-91 owner with no user entity or no passkeys gets an empty list`() {
        val bare = who("bare@mail.com", withEntity = false)
        val empty = who("empty@mail.com")

        for (o in listOf(bare, empty)) {
            val r = call("GET", "/api/v1/passkeys", o.key)
            assertThat(r.statusCode()).isEqualTo(200)
            assertThat(r.body()).contains("\"items\":[]")
        }
    }

    @Test
    fun `AC-92 removing the only passkey is 204, list is empty and the session stays open`() {
        val a = who("anton@mail.com")
        val id = passkey(a, Instant.parse("2026-10-01T10:00:00Z"))

        assertThat(call("DELETE", "/api/v1/passkeys/$id", a.key).statusCode()).isEqualTo(204)

        assertThat(call("GET", "/api/v1/passkeys", a.key).body()).contains("\"items\":[]")
        assertThat(call("GET", "/api/v1/me", a.key).statusCode()).isEqualTo(200)
        assertThat(credentials.findByCredentialId(Bytes.fromBase64(id))).isNull()
    }

    @Test
    fun `AC-97 another owner's passkey is not listed, not removable, and looks missing`() {
        val a = who("a@mail.com")
        val b = who("b@mail.com")
        val mine = passkey(a, Instant.parse("2026-10-01T10:00:00Z"))
        val theirs = passkey(b, Instant.parse("2026-10-01T11:00:00Z"))

        assertThat(ids(call("GET", "/api/v1/passkeys", a.key))).containsExactly(mine)
        val foreign = call("DELETE", "/api/v1/passkeys/$theirs", a.key)
        val missing = call("DELETE", "/api/v1/passkeys/${Bytes.random().toBase64UrlString()}", a.key)

        assertThat(foreign.statusCode()).isEqualTo(404)
        assertThat(foreign.body()).contains("\"code\":\"not-found\"")
        assertThat(missing.statusCode()).isEqualTo(404)
        assertThat(missing.body()).contains("\"code\":\"not-found\"")
        assertThat(ids(call("GET", "/api/v1/passkeys", b.key))).containsExactly(theirs)
    }

    @Test
    fun `malformed passkey id is 400 validation-failed`() {
        val a = who("anton@mail.com")

        val r = call("DELETE", "/api/v1/passkeys/not%20valid!", a.key)

        assertThat(r.statusCode()).isEqualTo(400)
        assertThat(r.body()).contains("\"code\":\"validation-failed\"")
    }

    @Test
    fun `passkey endpoints without a session are 401`() {
        assertThat(call("GET", "/api/v1/passkeys", null).statusCode()).isEqualTo(401)
        assertThat(call("DELETE", "/api/v1/passkeys/abc", null).statusCode()).isEqualTo(401)
    }
}
