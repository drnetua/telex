package telex.messaging.internal.account

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.identity.OwnerId
import telex.messaging.AccountUnlinked
import telex.messaging.LinkedAccountId

/**
 * Deletes a Linked Account, its sealed key and its chat list, and records [AccountUnlinked] in the event registry,
 * all in one transaction: "leaves nothing" rests on this commit, not on event delivery (ADR-0002).
 */
@Service
class AccountDeletion(
    private val rows: LinkedAccountRows,
    private val events: ApplicationEventPublisher,
) {
    /** The session the Owner's account held when it was deleted, or null when it did not exist. */
    @Transactional
    fun delete(
        owner: OwnerId,
        id: LinkedAccountId,
    ): DeletedRow? {
        val deleted = rows.deleteMine(owner, id) ?: return null
        events.publishEvent(AccountUnlinked(owner, id))
        return deleted
    }
}
