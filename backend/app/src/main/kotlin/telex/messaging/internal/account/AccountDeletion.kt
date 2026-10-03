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
    /** True when the Owner's account existed and is gone. */
    @Transactional
    fun delete(
        owner: OwnerId,
        id: LinkedAccountId,
    ): Boolean {
        if (!rows.deleteMine(owner, id)) return false
        events.publishEvent(AccountUnlinked(owner, id))
        return true
    }
}
