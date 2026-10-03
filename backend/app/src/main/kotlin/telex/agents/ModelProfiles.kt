package telex.agents

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import telex.agents.internal.profile.ProfileCommands
import telex.agents.internal.profile.ProfileQueries
import telex.identity.OwnerId
import telex.shared.DomainProblem

class ProfileNotFound : DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found")

class ProfileLimitReached : DomainProblem(HttpStatus.CONFLICT, "profile-limit-reached", "Profile limit reached")

class AiNotConfigured : DomainProblem(HttpStatus.CONFLICT, "ai-not-configured", "AI models aren't set up")

/** Read side of the Models page; a thin facade over the query logic in `internal.profile`. */
@Service
class ModelProfiles(
    private val queries: ProfileQueries,
    private val commands: ProfileCommands,
) {
    fun catalog(): ModelCatalogView = queries.catalogView()

    fun list(owner: OwnerId): ModelProfileListView = queries.list(owner)

    fun get(
        owner: OwnerId,
        ref: ProfileRef,
    ): ModelProfileView = queries.get(owner, ref)

    fun draft(
        owner: OwnerId,
        from: ProfileRef?,
    ): DraftView = queries.draft(owner, from)

    fun create(
        owner: OwnerId,
        name: String,
        slots: Map<ModelSlotKind, List<String>>,
        duplicatedFrom: ProfileRef? = null,
    ): ModelProfileView = commands.create(owner, name, slots, duplicatedFrom)

    fun update(
        owner: OwnerId,
        ref: ProfileRef,
        name: String,
        slots: Map<ModelSlotKind, List<String>>,
    ): ModelProfileView = commands.update(owner, ref, name, slots)

    fun delete(
        owner: OwnerId,
        ref: ProfileRef,
    ): ProfileDeletion = commands.delete(owner, ref)

    fun setDefault(
        owner: OwnerId,
        ref: ProfileRef,
    ): ProfileRef = commands.setDefault(owner, ref)
}
