package telex.agents

import telex.shared.TypedId
import java.util.UUID

@JvmInline
value class ModelProfileId(
    override val value: UUID,
) : TypedId
