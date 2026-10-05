package telex.messaging

import telex.identity.OwnerId

/** The chat-list sync of a Linked Account advanced. A hint only; the count is read from the API. */
data class LinkedAccountSyncProgressed(
    val ownerId: OwnerId,
    val linkedAccountId: LinkedAccountId,
)
