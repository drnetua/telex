package telex.messaging

import org.springframework.http.HttpStatus
import telex.shared.DomainProblem

/** AC-119: no Telegram app credentials (or no master key); the wizard is not started. */
class TelegramLinkingNotSetUp :
    DomainProblem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "telegram-linking-not-set-up",
        "Telegram linking isn't set up on this installation yet.",
    )

/** AC-115: the Owner already holds the installation's limit of Linked Accounts. */
class LinkedAccountLimitReached(
    limit: Int,
) : DomainProblem(
        HttpStatus.CONFLICT,
        "linked-account-limit-reached",
        "You have reached the limit of linked accounts.",
    ) {
    init {
        body.setProperty("limit", limit)
    }
}

/** The "Sign in again" target is not this Owner's account (indistinguishable from missing, AC-03). */
class LinkedAccountNotFound : DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found.")

/** The "Sign in again" target is not Session lost, so there is nothing to sign in again. */
class TelegramAccountAlreadyLinked :
    DomainProblem(HttpStatus.CONFLICT, "telegram-account-already-linked", "This Telegram account is already linked.")

/** AC-109 / AC-110: no open attempt (cancelled, expired, session ended, or teleX restarted). */
class LinkingAttemptNotFound :
    DomainProblem(HttpStatus.NOT_FOUND, "linking-attempt-not-found", "This linking attempt ended.")
