package telex.messaging

import telex.identity.OwnerId

/** A new Linked Account was added. Not published for "Sign in again". Carries teleX ids only. */
data class AccountLinked(
    val ownerId: OwnerId,
    val linkedAccountId: LinkedAccountId,
)
