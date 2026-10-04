package telex.messaging.internal.account

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.OwnerId
import telex.identity.OwnerKeys
import telex.messaging.AccountLinked
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountState
import telex.messaging.LinkedAccountStateChanged
import telex.messaging.MaskedPhone
import telex.messaging.internal.config.AccountLimit
import telex.messaging.keyAad
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import telex.telegram.TelegramUser
import java.time.Clock

/** What became of an authorized attempt once the rules ran. */
sealed interface Completion {
    data class Linked(
        val id: LinkedAccountId,
    ) : Completion

    /** [replaced] is the session the account had before, to be destroyed after the commit (null after a key reset). */
    data class SignedInAgain(
        val id: LinkedAccountId,
        val replaced: TelegramSessionId?,
    ) : Completion

    data class Refused(
        val reason: LinkRefusal,
    ) : Completion
}

/**
 * Turns an authorized Telegram sign-in into a Linked Account (new link, or sign in again) or a refusal, in one
 * transaction under the Owner's advisory lock (AC-01, AC-04, AC-108, AC-115, AC-117).
 */
@Service
class LinkCompletion(
    private val rows: LinkedAccountRows,
    private val ownerKeys: OwnerKeys,
    private val limit: AccountLimit,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun complete(
        owner: OwnerId,
        target: LinkedAccountId?,
        sessionId: TelegramSessionId,
        dbKey: ByteArray,
        user: TelegramUser,
    ): Completion {
        val count = rows.countMineLocked(owner)
        val targetAccount = target?.let { rows.getMine(owner, it) }
        if (target != null && targetAccount == null) return Completion.Refused(LinkRefusal.MISMATCH)
        val existing = rows.findByTelegramUser(user.telegramUserId)
        return when (val decision = LinkRules.decide(owner, targetAccount, existing, count, limit.maxPerOwner)) {
            is LinkDecision.Refused -> Completion.Refused(decision.reason)
            is LinkDecision.SignedInAgain -> signInAgain(owner, decision.account, sessionId, dbKey, user)
            LinkDecision.NewLink -> link(owner, sessionId, dbKey, user)
        }
    }

    /** The reason a concurrent insert of the same Telegram user lost to the unique index. */
    @Transactional(readOnly = true)
    fun afterRace(
        owner: OwnerId,
        user: TelegramUser,
    ): Completion.Refused {
        val winner = rows.findByTelegramUser(user.telegramUserId)
        return Completion.Refused(
            if (winner != null && winner.ownerId == owner) LinkRefusal.ALREADY_LINKED else LinkRefusal.OTHER_OWNER,
        )
    }

    private fun link(
        owner: OwnerId,
        sessionId: TelegramSessionId,
        dbKey: ByteArray,
        user: TelegramUser,
    ): Completion {
        val id = LinkedAccountId(Uuid7.next())
        rows.insert(
            NewLinkedAccount(
                id,
                owner,
                user.telegramUserId,
                sessionId,
                ownerKeys.seal(owner, dbKey, id.keyAad()),
                user.displayName,
                MaskedPhone(user.phoneCountryCode, user.phoneLastTwo),
                clock.instant(),
            ),
        )
        events.publishEvent(AccountLinked(owner, id))
        return Completion.Linked(id)
    }

    private fun signInAgain(
        owner: OwnerId,
        account: LinkedAccount,
        sessionId: TelegramSessionId,
        dbKey: ByteArray,
        user: TelegramUser,
    ): Completion {
        val swapped =
            rows.swapSession(account.id, sessionId, ownerKeys.seal(owner, dbKey, account.id.keyAad()), user.displayName)
        // An unlink deleted the account since it was read: nothing holds the new session, so it is refused, not kept.
        if (swapped == 0) return Completion.Refused(LinkRefusal.MISMATCH)
        events.publishEvent(LinkedAccountStateChanged(owner, account.id, LinkedAccountState.CONNECTED))
        return Completion.SignedInAgain(account.id, account.telegramSessionId)
    }
}
