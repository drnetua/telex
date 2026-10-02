package telex.identity.internal.grant

import org.springframework.http.HttpStatus
import telex.shared.DomainProblem
import java.time.Instant

/** A sign-in grant that cannot be used; known grants carry the `email` extension (AC-35, AC-103). */
class GrantRefused(
    code: String,
    detail: String,
    email: String?,
) : DomainProblem(HttpStatus.GONE, code, detail) {
    init {
        if (email != null) body.setProperty("email", email)
    }
}

/** Classifies a grant for preview; redeem (T6) reuses it. Returns null when the grant is usable. */
object GrantRefusals {
    fun unknown() = GrantRefused("sign-in-link-expired", "This sign-in link has expired.", null)

    fun classify(
        row: GrantRow,
        now: Instant,
    ): GrantRefused? =
        when {
            row.usedAt != null -> {
                GrantRefused("sign-in-link-used", "This sign-in link was already used.", row.email)
            }

            row.supersededAt != null || !now.isBefore(row.expiresAt) -> {
                GrantRefused("sign-in-link-expired", "This sign-in link has expired.", row.email)
            }

            row.wrongAttempts >= MAX_WRONG -> {
                GrantRefused("sign-in-grant-void", "Too many wrong codes for this sign-in email.", row.email)
            }

            else -> {
                null
            }
        }

    private const val MAX_WRONG = 5
}
