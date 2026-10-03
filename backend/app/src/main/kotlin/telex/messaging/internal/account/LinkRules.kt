package telex.messaging.internal.account

import telex.identity.OwnerId
import telex.messaging.LinkedAccountState

enum class LinkRefusal { OTHER_OWNER, ALREADY_LINKED, LIMIT, MISMATCH }

sealed interface LinkDecision {
    data object NewLink : LinkDecision

    data class SignedInAgain(
        val account: LinkedAccount,
    ) : LinkDecision

    data class Refused(
        val reason: LinkRefusal,
    ) : LinkDecision
}

/** The Linked Account rules (AC-04, AC-108, AC-115, AC-117) as one pure decision. */
object LinkRules {
    /**
     * @param owner the caller
     * @param signInAgainTarget the Owner's Session lost account being re-signed-in, when the wizard started so
     * @param existing the account that already holds the authorized Telegram user, whoever owns it
     * @param count the caller's Linked Accounts (Session lost ones included)
     * @param limit the installation's per-Owner cap
     */
    fun decide(
        owner: OwnerId,
        signInAgainTarget: LinkedAccount?,
        existing: LinkedAccount?,
        count: Int,
        limit: Int,
    ): LinkDecision =
        when {
            existing != null && existing.ownerId != owner -> LinkDecision.Refused(LinkRefusal.OTHER_OWNER)
            signInAgainTarget != null -> signInAgain(signInAgainTarget, existing)
            existing != null -> sameOwner(existing)
            count >= limit -> LinkDecision.Refused(LinkRefusal.LIMIT)
            else -> LinkDecision.NewLink
        }

    private fun signInAgain(
        target: LinkedAccount,
        existing: LinkedAccount?,
    ): LinkDecision =
        if (existing == null || existing.id != target.id) {
            LinkDecision.Refused(LinkRefusal.MISMATCH)
        } else {
            sameOwner(existing)
        }

    private fun sameOwner(existing: LinkedAccount): LinkDecision =
        if (existing.state == LinkedAccountState.SESSION_LOST) {
            LinkDecision.SignedInAgain(existing)
        } else {
            LinkDecision.Refused(LinkRefusal.ALREADY_LINKED)
        }
}
