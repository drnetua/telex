package telex.identity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.identity.internal.secret.Secrets
import telex.mail.RecordingMailer
import telex.mail.RecordingMailerConfiguration
import telex.shared.DomainProblem
import telex.shared.Uuid7
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/** AC-83 (incomplete address), AC-86 (preview is read-only), AC-103 (newest wins), AC-35 (15 minutes). */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedClockConfiguration::class, RecordingMailerConfiguration::class)
class SignInIssueIT {
    @Autowired lateinit var signIn: SignIn

    @Autowired lateinit var mailer: RecordingMailer

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var clock: MutableClock

    private val start = Instant.parse("2026-10-02T14:00:00Z")

    @BeforeEach
    fun reset() {
        clock.set(start)
        mailer.reset()
        jdbc.execute("DELETE FROM sign_in_grant")
    }

    private fun grantCount() = jdbc.queryForObject("SELECT count(*) FROM sign_in_grant", Int::class.java)

    private fun supersededAt(id: SignInGrantId): Instant? =
        jdbc
            .queryForObject(
                "SELECT superseded_at FROM sign_in_grant WHERE id = ?",
                Timestamp::class.java,
                id.value,
            )?.toInstant()

    private fun tokenFromMail(): String = Regex("#([A-Za-z0-9_-]+)").find(mailer.sent.last().text)!!.groupValues[1]

    private fun codeFromMail(): String = Regex("\\b(\\d{6})\\b").find(mailer.sent.last().text)!!.groupValues[1]

    private fun problem(block: () -> Unit): DomainProblem {
        val t = runCatching(block).exceptionOrNull()
        assertThat(t).isInstanceOf(DomainProblem::class.java)
        return t as DomainProblem
    }

    private fun DomainProblem.code() = body.properties?.get("code")

    private fun DomainProblem.email() = body.properties?.get("email")

    private fun seed(
        email: String = "anton@mail.com",
        issued: Instant = clock.instant(),
        used: Boolean = false,
        superseded: Boolean = false,
        wrong: Int = 0,
    ): String {
        val id = Uuid7.next()
        val token = Secrets.newToken()
        jdbc.update(
            "INSERT INTO sign_in_grant (id, email, canonical_email, link_token_hash, code_hash, wrong_attempts," +
                " issued_at, expires_at, used_at, superseded_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,
            email,
            email.lowercase(),
            Secrets.sha256(token),
            Secrets.codeHash(SignInGrantId(id), "123456"),
            wrong,
            Timestamp.from(issued),
            Timestamp.from(issued.plus(Duration.ofMinutes(15))),
            if (used) Timestamp.from(issued) else null,
            if (superseded) Timestamp.from(issued) else null,
        )
        return token
    }

    @Test
    fun `AC-83 an incomplete address sends nothing and stores nothing`() {
        listOf("me@localhost", "me", "@example.com", "me@", "").forEach { raw ->
            val p = problem { signIn.request(raw) }
            assertThat(p.code()).isEqualTo("validation-failed")
            assertThat(p.statusCode.value()).isEqualTo(400)
        }
        assertThat(mailer.sent).isEmpty()
        assertThat(grantCount()).isZero()
    }

    @Test
    fun `a request stores only hashes and sends one email to the address as typed with link and code`() {
        val issued = signIn.request("Anton+work@Mail.com")
        assertThat(issued.emailAsTyped).isEqualTo("Anton+work@Mail.com")
        assertThat(mailer.sent).hasSize(1)
        val mail = mailer.sent.single()
        assertThat(mail.to).isEqualTo("Anton+work@Mail.com")
        val token = tokenFromMail()
        val code = codeFromMail()
        assertThat(mail.text).contains("http://localhost:8080/sign-in/link#$token")
        val row = jdbc.queryForMap("SELECT * FROM sign_in_grant WHERE id = ?", issued.grantId.value)
        assertThat(row["canonical_email"]).isEqualTo("anton@mail.com")
        assertThat(row["link_token_hash"] as ByteArray).isEqualTo(Secrets.sha256(token))
        assertThat(row["code_hash"] as ByteArray).isEqualTo(Secrets.codeHash(issued.grantId, code))
        assertThat(
            (row["expires_at"] as Timestamp).toInstant(),
        ).isEqualTo(start.plus(Duration.ofMinutes(15)))
        assertThat(row.values.filterIsInstance<String>().joinToString(" ")).doesNotContain(token).doesNotContain(code)
    }

    @Test
    fun `AC-103 a new request supersedes every live grant of the canonical address`() {
        val first = signIn.request("anton@mail.com")
        val firstToken = tokenFromMail()
        val other = signIn.request("someone@mail.com")
        val second = signIn.request("Anton+work@Mail.com")
        assertThat(supersededAt(first.grantId)).isEqualTo(start)
        assertThat(supersededAt(second.grantId)).isNull()
        assertThat(supersededAt(other.grantId)).isNull()
        val p = problem { signIn.preview(firstToken) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-expired")
        assertThat(signIn.preview(tokenFromMail()).email).isEqualTo("Anton+work@Mail.com")
    }

    @Test
    fun `AC-103 parallel requests for one address leave exactly one live grant`() {
        val pool = Executors.newFixedThreadPool(PARALLEL)
        try {
            repeat(ROUNDS) {
                jdbc.execute("DELETE FROM sign_in_grant")
                val gate = CyclicBarrier(PARALLEL)
                val calls =
                    (1..PARALLEL).map {
                        pool.submit {
                            gate.await()
                            signIn.request("Race+$it@mail.com")
                        }
                    }
                calls.forEach { it.get() }
                assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM sign_in_grant WHERE used_at IS NULL AND superseded_at IS NULL",
                        Int::class.java,
                    ),
                ).isEqualTo(1)
                assertThat(grantCount()).isEqualTo(PARALLEL)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a mail failure rolls back the new grant and leaves the earlier one live`() {
        val first = signIn.request("anton@mail.com")
        mailer.failing = true
        val p = problem { signIn.request("anton@mail.com") }
        assertThat(p.statusCode.value()).isEqualTo(503)
        assertThat(p.code()).isEqualTo("mail-unavailable")
        assertThat(grantCount()).isEqualTo(1)
        assertThat(supersededAt(first.grantId)).isNull()
    }

    @Test
    fun `AC-86 preview within 15 minutes returns the address and changes nothing`() {
        val token = seed("Anton@mail.com")
        val before = jdbc.queryForMap("SELECT * FROM sign_in_grant")
        clock.set(start.plus(Duration.ofMinutes(5)))
        assertThat(signIn.preview(token).email).isEqualTo("Anton@mail.com")
        assertThat(signIn.preview(token).email).isEqualTo("Anton@mail.com")
        assertThat(jdbc.queryForMap("SELECT * FROM sign_in_grant")).usingRecursiveComparison().isEqualTo(before)
    }

    @Test
    fun `AC-35 preview after 15 minutes is refused as expired with the address`() {
        val token = seed()
        clock.set(start.plus(Duration.ofMinutes(16)))
        val p = problem { signIn.preview(token) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-expired")
        assertThat(p.email()).isEqualTo("anton@mail.com")
    }

    @Test
    fun `preview of a used grant is refused as used`() {
        val p = problem { signIn.preview(seed(used = true)) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-used")
        assertThat(p.email()).isEqualTo("anton@mail.com")
    }

    @Test
    fun `preview of a grant with five wrong codes is refused as void`() {
        val p = problem { signIn.preview(seed(wrong = 5)) }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-grant-void")
        assertThat(p.email()).isEqualTo("anton@mail.com")
    }

    @Test
    fun `preview of a superseded grant is refused as expired`() {
        val p = problem { signIn.preview(seed(superseded = true)) }
        assertThat(p.code()).isEqualTo("sign-in-link-expired")
    }

    @Test
    fun `preview of an unknown token is refused as expired without an address`() {
        val p = problem { signIn.preview("no-such-token") }
        assertThat(p.statusCode.value()).isEqualTo(410)
        assertThat(p.code()).isEqualTo("sign-in-link-expired")
        assertThat(p.email()).isNull()
        assertThatThrownBy { signIn.preview("") }.isInstanceOf(DomainProblem::class.java)
    }

    private companion object {
        const val PARALLEL = 8
        const val ROUNDS = 5
    }
}
