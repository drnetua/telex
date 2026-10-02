package telex.identity

import telex.shared.TypedId
import java.util.UUID

@JvmInline value class OwnerId(
    override val value: UUID,
) : TypedId

@JvmInline value class SignInGrantId(
    override val value: UUID,
) : TypedId

@JvmInline value class SignInSessionId(
    override val value: UUID,
) : TypedId
