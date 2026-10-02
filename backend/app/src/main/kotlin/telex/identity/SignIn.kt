package telex.identity

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.internal.email.SignInEmail
import telex.identity.internal.grant.GrantRefusals
import telex.identity.internal.grant.GrantRefused
import telex.identity.internal.grant.GrantRow
import telex.identity.internal.grant.GrantRows
import telex.identity.internal.owner.EmailAddress
import telex.identity.internal.owner.Owners
import telex.identity.internal.secret.Secrets
import telex.mail.MailUnavailable
import telex.mail.Mailer
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import telex.shared.Uuid7
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class GrantIssued(
    val grantId: SignInGrantId,
    val emailAsTyped: String,
)

data class SignedIn(
    val key: String,
    val createdAccount: Boolean,
)

data class LinkPreview(
    val email: String,
)

/** Email sign-in: issue a grant with its email, and read a link without redeeming it (ADR-0003). */
@Service
class SignIn(
    private val grants: GrantRows,
    private val owners: Owners,
    private val sessions: SignInSessions,
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

    @Transactional
    fun redeemByLink(
        linkToken: String,
        heldKey: String?,
        userAgent: String?,
        timeZone: String?,
    ): SignedIn {
        val now = clock.instant()
        val hash = Secrets.sha256(linkToken)
        val row =
            grants.redeemByLink(hash, now)
                ?: throw refused(grants.findByLinkHash(hash), now)
        return signedIn(row, "link", heldKey, userAgent, timeZone)
    }

    /** A wrong code must persist its attempt while still reporting the refusal, so refusals do not roll back. */
    @Transactional(noRollbackFor = [DomainProblem::class])
    fun redeemByCode(
        grantId: SignInGrantId,
        code: String,
        heldKey: String?,
        userAgent: String?,
        timeZone: String?,
    ): SignedIn {
        val now = clock.instant()
        val known = grants.findById(grantId)
        if (known == null || GrantRefusals.classify(known, now) != null) throw refused(known, now)
        val row = grants.redeemByCode(grantId, Secrets.codeHash(grantId, code), now)
        if (row != null) return signedIn(row, "code", heldKey, userAgent, timeZone)
        throw wrongCode(known, now)
    }

    private fun wrongCode(
        known: GrantRow,
        now: Instant,
    ): DomainProblem {
        val wrong = grants.addWrongAttempt(known.id, now) ?: return refused(grants.findById(known.id), now)
        meters.counter("telex.signin.refused", "reason", "wrong_code").increment()
        return if (wrong >= MAX_WRONG) {
            GrantRefused("sign-in-grant-void", "Too many wrong codes for this sign-in email.", known.email)
        } else {
            DomainProblem(HttpStatus.UNPROCESSABLE_CONTENT, "sign-in-code-wrong", "That code is not right.")
                .also { it.body.setProperty("attemptsLeft", MAX_WRONG - wrong) }
        }
    }

    private fun refused(
        row: GrantRow?,
        now: Instant,
    ): GrantRefused = count(row?.let { GrantRefusals.classify(it, now) } ?: GrantRefusals.unknown())

    private fun count(problem: GrantRefused): GrantRefused {
        val code = problem.body.properties?.get("code") as String
        meters
            .counter(
                "telex.signin.refused",
                "reason",
                code.removePrefix("sign-in-").removePrefix("link-"),
            ).increment()
        return problem
    }

    private fun signedIn(
        row: GrantRow,
        method: String,
        heldKey: String?,
        userAgent: String?,
        timeZone: String?,
    ): SignedIn {
        val (ownerId, created) = owners.findOrCreate(row.email, row.canonicalEmail, clock.instant())
        val session = sessions.start(ownerId, heldKey, userAgent, timeZone, created)
        meters.counter("telex.signin.redeemed", "method", method).increment()
        return SignedIn(session.key, created)
    }

    private companion object {
        const val MAX_WRONG = 5
        val LIFETIME: Duration = Duration.ofMinutes(15)
    }
}
