package telex.messaging

import telex.identity.OwnerId

/** A Linked Account was deleted; recorded in the deleting transaction. Carries teleX ids only. */
data class AccountUnlinked(
    val ownerId: OwnerId,
    val linkedAccountId: LinkedAccountId,
)
