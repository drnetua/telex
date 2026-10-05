package telex.messaging

import telex.identity.OwnerId

/** A Linked Account moved to [state]. Carries teleX ids and the state only. */
data class LinkedAccountStateChanged(
    val ownerId: OwnerId,
    val linkedAccountId: LinkedAccountId,
    val state: LinkedAccountState,
)
