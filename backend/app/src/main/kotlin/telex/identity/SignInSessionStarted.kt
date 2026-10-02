package telex.identity

/** Published in the transaction that inserts the session. Carries no email address and no secrets. */
data class SignInSessionStarted(
    val ownerId: OwnerId,
    val sessionId: SignInSessionId,
    val createdAccount: Boolean,
)
