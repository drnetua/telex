package telex.identity

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.internal.email.SignInEmail
import telex.identity.internal.grant.GrantRefusals
import telex.identity.internal.grant.GrantRows
import telex.identity.internal.owner.EmailAddress
import telex.identity.internal.secret.Secrets
import telex.mail.MailUnavailable
import telex.mail.Mailer
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import telex.shared.Uuid7
import java.time.Clock
import java.time.Duration

data class GrantIssued(
    val grantId: SignInGrantId,
    val emailAsTyped: String,
)

data class LinkPreview(
    val email: String,
)

/** Email sign-in: issue a grant with its email, and read a link without redeeming it (ADR-0003). */
@Service
class SignIn(
    private val grants: GrantRows,
    private val mailer: Mailer,
    private val publicUrl: PublicUrl,
    private val clock: Clock,
    private val meters: MeterRegistry,
) {
    @Transactional
    fun request(rawEmail: String): GrantIssued {
        val address =
            EmailAddress.parse(rawEmail)
                ?: throw DomainProblem(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Enter a complete email address.",
                    listOf(FieldProblem("email", "email-incomplete", "Enter a complete email address.")),
                )
        val now = clock.instant()
        val id = SignInGrantId(Uuid7.next())
        val token = Secrets.newToken()
        val code = Secrets.newCode()
        grants.supersedeLive(address.canonical, now)
        grants.insert(
            id,
            address.asTyped,
            address.canonical,
            Secrets.sha256(token),
            Secrets.codeHash(id, code),
            now,
            now.plus(LIFETIME),
        )
        try {
            mailer.send(SignInEmail.build(address.asTyped, publicUrl.link("/sign-in/link", token), code))
        } catch (e: MailUnavailable) {
            throw DomainProblem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "mail-unavailable",
                "The sign-in email could not be sent. Try again.",
                cause = e,
            )
        }
        meters.counter("telex.signin.grants.issued").increment()
        return GrantIssued(id, address.asTyped)
    }

    @Transactional(readOnly = true)
    fun preview(linkToken: String): LinkPreview {
        val row = grants.findByLinkHash(Secrets.sha256(linkToken)) ?: throw GrantRefusals.unknown()
        GrantRefusals.classify(row, clock.instant())?.let { throw it }
        return LinkPreview(row.email)
    }

    private companion object {
        val LIFETIME: Duration = Duration.ofMinutes(15)
    }
}
