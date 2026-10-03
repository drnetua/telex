package telex.agents.internal.profile

import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import telex.agents.AiNotConfigured
import telex.agents.ModelProfileDeleted
import telex.agents.ModelProfileId
import telex.agents.ModelProfileView
import telex.agents.ModelSlotKind
import telex.agents.NoTextModel
import telex.agents.ProfileDeletion
import telex.agents.ProfileInvalid
import telex.agents.ProfileLimitReached
import telex.agents.ProfileNotFound
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.agents.SystemProfileReadOnly
import telex.identity.OwnerId
import telex.llm.CatalogModel
import telex.llm.CatalogState
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.shared.FieldProblem
import telex.shared.Uuid7
import java.time.Clock

/** Write side of the Models page: each command is one transaction (sad.md flows 4-6). */
@Component
class ProfileCommands(
    private val catalog: ModelCatalog,
    private val systemProfiles: SystemProfiles,
    private val profiles: ProfileRepository,
    private val defaults: DefaultProfileRepository,
    private val queries: ProfileQueries,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional(rollbackFor = [Exception::class])
    fun create(
        owner: OwnerId,
        name: String,
        slots: Map<ModelSlotKind, List<String>>,
        duplicatedFrom: ProfileRef?,
    ): ModelProfileView {
        requireAiConfigured()
        val baseline = duplicatedFrom?.let { source(owner, it) }.orEmpty()
        profiles.lockOwner(owner)
        if (!ProfileRules.canCreate(profiles.count(owner))) throw ProfileLimitReached()
        val draft = draftOf(name, slots)
        check(draft, profiles.names(owner), baseline)
        val id = ModelProfileId(Uuid7.next())
        try {
            profiles.insert(owner, ModelProfile(id, draft.name.trim(), draft.slots), clock.instant())
        } catch (_: DuplicateKeyException) {
            throw nameTaken()
        }
        return queries.get(owner, ProfileRef.Custom(id))
    }

    @Transactional(rollbackFor = [Exception::class])
    fun update(
        owner: OwnerId,
        ref: ProfileRef,
        name: String,
        slots: Map<ModelSlotKind, List<String>>,
    ): ModelProfileView {
        requireAiConfigured()
        val id = customId(ref)
        val stored = profiles.find(owner, id) ?: throw ProfileNotFound()
        val draft = draftOf(name, slots)
        check(draft, profiles.list(owner).filter { it.id != id }.map { it.name }, stored.slots)
        try {
            profiles.update(owner, ModelProfile(id, draft.name.trim(), draft.slots))
        } catch (_: DuplicateKeyException) {
            throw nameTaken()
        }
        return queries.get(owner, ref)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun delete(
        owner: OwnerId,
        ref: ProfileRef,
    ): ProfileDeletion {
        val id = customId(ref)
        val wasDefault = defaults.clearIfCustom(owner, id)
        if (!profiles.delete(owner, id)) throw ProfileNotFound()
        events.publishEvent(ModelProfileDeleted(owner, id, wasDefault))
        return ProfileDeletion(defaults.get(owner) ?: BALANCED, wasDefault)
    }

    @Transactional(rollbackFor = [Exception::class])
    fun setDefault(
        owner: OwnerId,
        ref: ProfileRef,
    ): ProfileRef {
        val slots = source(owner, ref)
        val text =
            SlotResolution.resolve(
                ModelSlotKind.TEXT,
                slots[ModelSlotKind.TEXT].orEmpty(),
                catalog.snapshot().models,
            )
        if (!SlotResolution.choosable(text)) throw NoTextModel()
        defaults.set(owner, ref, clock.instant())
        return ref
    }

    private fun requireAiConfigured() {
        if (catalog.snapshot().state == CatalogState.NOT_CONFIGURED) throw AiNotConfigured()
    }

    private fun customId(ref: ProfileRef): ModelProfileId =
        when (ref) {
            is ProfileRef.System -> throw SystemProfileReadOnly()
            is ProfileRef.Custom -> ref.id
        }

    private fun source(
        owner: OwnerId,
        ref: ProfileRef,
    ): Map<ModelSlotKind, List<ModelId>> =
        when (ref) {
            is ProfileRef.System -> systemProfiles.profile(ref.key).slots
            is ProfileRef.Custom -> (profiles.find(owner, ref.id) ?: throw ProfileNotFound()).slots
        }

    private fun draftOf(
        name: String,
        slots: Map<ModelSlotKind, List<String>>,
    ) = ProfileDraft(name, ModelSlotKind.entries.associateWith { kind -> slots[kind].orEmpty().map(::ModelId) })

    private fun check(
        draft: ProfileDraft,
        ownNames: Collection<String>,
        baseline: Map<ModelSlotKind, List<ModelId>>,
    ) {
        val models = catalog.snapshot().models
        val errors = ProfileRules.validate(draft, models, ownNames, baseline)
        if (errors.isNotEmpty()) throw ProfileInvalid(errors.map { FieldProblems.of(it, draft, models) })
    }

    private fun nameTaken() =
        ProfileInvalid(listOf(FieldProblem("name", "name-taken", "You already have a profile with this name.")))

    private companion object {
        val BALANCED = ProfileRef.System(SystemProfileKey.BALANCED)
    }
}
