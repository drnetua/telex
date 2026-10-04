package telex.agents

import telex.identity.OwnerId

/** Published in the delete transaction (events.md); no consumers in E10. */
data class ModelProfileDeleted(
    val ownerId: OwnerId,
    val profileId: ModelProfileId,
    val wasDefault: Boolean,
)
