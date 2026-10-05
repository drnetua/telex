package telex.messaging.internal.lifecycle

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import telex.messaging.LinkedAccountState
import telex.messaging.LinkedAccountStateChanged
import telex.messaging.internal.account.LinkedAccountRows
import telex.telegram.TelegramSessionId

/** Stores a Linked Account's state and publishes [LinkedAccountStateChanged] only when the stored state changed. */
@Service
class LinkedAccountStates(
    private val rows: LinkedAccountRows,
    private val events: ApplicationEventPublisher,
) {
    /** True when the account holding [sessionId] really moved to [to]; an unknown session or no change is false. */
    @Transactional
    fun transition(
        sessionId: TelegramSessionId,
        to: LinkedAccountState,
    ): Boolean {
        val (account, owner) = rows.transitionBySession(sessionId, to) ?: return false
        events.publishEvent(LinkedAccountStateChanged(owner, account, to))
        return true
    }

    /** Master-key reset (sad §7): every account becomes Session lost and loses its session and sealed key. */
    @Transactional
    fun resetAll() {
        rows.resetAll().forEach { (account, owner) ->
            events.publishEvent(LinkedAccountStateChanged(owner, account, LinkedAccountState.SESSION_LOST))
        }
    }
}
