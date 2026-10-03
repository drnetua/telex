package telex.messaging

import org.springframework.http.HttpStatus
import telex.shared.DomainProblem
import java.time.Instant

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

/** AC-04: the Telegram account is another Owner's Linked Account; the sign-in just made was ended. */
class TelegramAccountOwnedByAnotherOwner :
    DomainProblem(
        HttpStatus.CONFLICT,
        "telegram-account-owned-by-another-owner",
        "This Telegram account is linked to another teleX account.",
    )

/** AC-117: "Sign in again" signed in to a different Telegram account; the sign-in was ended. */
class TelegramAccountMismatch :
    DomainProblem(HttpStatus.CONFLICT, "telegram-account-mismatch", "You signed in to a different Telegram account.")

/** AC-109 / AC-110: no open attempt (cancelled, expired, session ended, or teleX restarted). */
class LinkingAttemptNotFound :
    DomainProblem(HttpStatus.NOT_FOUND, "linking-attempt-not-found", "This linking attempt ended.")

/** The step does not match where the attempt is (a stale tab); nothing changed (api-sync §B 4, gap 1). */
class LinkingStepMismatch(
    step: LinkingStep,
) : DomainProblem(HttpStatus.CONFLICT, "linking-step-mismatch", "The linking attempt is at another step.") {
    init {
        body.setProperty("step", step.name.lowercase())
    }
}

/** AC-107: the number is not a valid phone number. */
class TelegramPhoneInvalid :
    DomainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "telegram-phone-invalid", "This isn't a valid phone number.")

/** AC-107: no Telegram account uses the number; teleX never creates one. */
class TelegramPhoneUnregistered :
    DomainProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "telegram-phone-unregistered",
        "No Telegram account uses this number.",
    )

/** AC-107: Telegram has banned the number. */
class TelegramPhoneBanned :
    DomainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "telegram-phone-banned", "Telegram has banned this number.")

/** AC-02: the code is wrong. */
class TelegramCodeWrong :
    DomainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "telegram-code-wrong", "The code is wrong.")

/** AC-02: the code has expired. */
class TelegramCodeExpired :
    DomainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "telegram-code-expired", "The code has expired.")

/** AC-106: the two-step verification password is wrong; [hint] is the Owner's own hint, null when none. */
class TelegramPasswordWrong(
    hint: String?,
) : DomainProblem(HttpStatus.UNPROCESSABLE_ENTITY, "telegram-password-wrong", "The password is wrong.") {
    init {
        body.setProperty("passwordHint", hint)
    }
}

/** AC-02: Telegram limits the attempts; the attempt ended and the Owner can try again at [retryAt]. */
class TelegramWaitRequired(
    retryAt: Instant,
) : DomainProblem(
        HttpStatus.TOO_MANY_REQUESTS,
        "telegram-wait-required",
        "Telegram asks you to wait before trying again.",
    ) {
    init {
        body.setProperty("retryAt", retryAt.toString())
    }
}

/** Telegram did not answer the step in time; the attempt stays at its step. */
class TelegramUnavailableProblem :
    DomainProblem(HttpStatus.SERVICE_UNAVAILABLE, "telegram-unavailable", "Telegram can't be reached right now.")
