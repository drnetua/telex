package telex.web.live

import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import telex.messaging.AccountLinked
import telex.messaging.AccountUnlinked
import telex.messaging.LinkedAccountStateChanged
import telex.messaging.LinkedAccountSyncProgressed

/** Turns Linked Account events into a `linked-accounts` hint for their Owner. A repeated delivery is harmless. */
@Component
class LiveHintListeners(
    private val registry: EmitterRegistry,
) {
    @ApplicationModuleListener
    fun on(event: AccountLinked) = registry.hint(event.ownerId, LiveHint.LINKED_ACCOUNTS)

    @ApplicationModuleListener
    fun on(event: AccountUnlinked) = registry.hint(event.ownerId, LiveHint.LINKED_ACCOUNTS)

    @ApplicationModuleListener
    fun on(event: LinkedAccountStateChanged) = registry.hint(event.ownerId, LiveHint.LINKED_ACCOUNTS)

    @ApplicationModuleListener
    fun on(event: LinkedAccountSyncProgressed) = registry.hint(event.ownerId, LiveHint.LINKED_ACCOUNTS)
}
