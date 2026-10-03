package telex.messaging

import telex.shared.TypedId
import java.util.UUID

@JvmInline value class ChannelId(
    override val value: UUID,
) : TypedId
