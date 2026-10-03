package telex.agents.internal.profile

import org.springframework.stereotype.Component
import telex.agents.AiNotConfigured
import telex.agents.AvailabilityView
import telex.agents.CatalogModelView
import telex.agents.CatalogViewState
import telex.agents.ChainModelView
import telex.agents.DraftView
import telex.agents.ModelCatalogView
import telex.agents.ModelProfileListView
import telex.agents.ModelProfileView
import telex.agents.ModelSlotKind
import telex.agents.PriceView
import telex.agents.PriceViewState
import telex.agents.ProfileLimitReached
import telex.agents.ProfileNotFound
import telex.agents.ProfileRef
import telex.agents.SlotViewState
import telex.agents.SystemProfileKey
import telex.llm.CatalogModel
import telex.llm.CatalogSnapshot
import telex.llm.CatalogState
import telex.llm.ModelCatalog
import telex.llm.ModelId
import telex.agents.SlotView as SlotViewOut

/** Read model of the Models page. Serves from the in-memory snapshot and never calls the provider. */
@Component
class ProfileQueries(
    private val catalog: ModelCatalog,
    private val systemProfiles: SystemProfiles,
    private val profiles: ProfileRepository,
    private val defaults: DefaultProfileRepository,
) {
    fun catalogView(): ModelCatalogView {
        val snapshot = catalog.snapshot()
        return ModelCatalogView(
            state = CatalogViewState.valueOf(snapshot.state.name),
            lastRefreshedAt = snapshot.refreshedAt,
            lastFailedAt = snapshot.failedAt,
            models =
                snapshot.models.values
                    .filter { it.slots.isNotEmpty() }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.modelId.value }))
                    .map(::toView),
        )
    }

    fun list(owner: telex.identity.OwnerId): ModelProfileListView {
        val snapshot = catalog.snapshot()
        val system = systemProfiles.all().map { view(ProfileRef.System(it.key), it.displayName, it.slots, snapshot) }
        val own =
            profiles.list(owner).map { view(ProfileRef.Custom(it.id), it.name, it.slots, snapshot) }
        return ModelProfileListView(
            aiConfigured = snapshot.state != CatalogState.NOT_CONFIGURED,
            defaultProfile = defaults.get(owner) ?: ProfileRef.System(SystemProfileKey.BALANCED),
            customProfileLimit = ProfileRules.MAX_CUSTOM_PROFILES,
            items = system + own,
        )
    }

    fun get(
        owner: telex.identity.OwnerId,
        ref: ProfileRef,
    ): ModelProfileView {
        val (name, slots) = source(owner, ref)
        return view(ref, name, slots, catalog.snapshot())
    }

    /** Order of checks: AI not configured, then the source profile (not found), then the 20-profile limit. */
    fun draft(
        owner: telex.identity.OwnerId,
        from: ProfileRef?,
    ): DraftView {
        val snapshot = catalog.snapshot()
        if (snapshot.state == CatalogState.NOT_CONFIGURED) throw AiNotConfigured()
        val origin = from?.let { source(owner, it) }
        if (!ProfileRules.canCreate(profiles.count(owner))) throw ProfileLimitReached()
        val name = origin?.let { ProfileRules.copyName(it.first, profiles.names(owner)) }.orEmpty()
        val slots = origin?.second ?: ModelSlotKind.entries.associateWith { emptyList() }
        val resolved = resolve(slots, snapshot)
        return DraftView(name, from, resolved.first, price(resolved.second, snapshot))
    }

    private fun source(
        owner: telex.identity.OwnerId,
        ref: ProfileRef,
    ): Pair<String, Map<ModelSlotKind, List<ModelId>>> =
        when (ref) {
            is ProfileRef.System -> {
                systemProfiles.profile(ref.key).let { it.displayName to it.slots }
            }

            is ProfileRef.Custom -> {
                (
                    profiles.find(
                        owner,
                        ref.id,
                    ) ?: throw ProfileNotFound()
                ).let { it.name to it.slots }
            }
        }

    private fun view(
        ref: ProfileRef,
        name: String,
        slots: Map<ModelSlotKind, List<ModelId>>,
        snapshot: CatalogSnapshot,
    ): ModelProfileView {
        val (views, text) = resolve(slots, snapshot)
        return ModelProfileView(ref, name, views, price(text, snapshot), SlotResolution.choosable(text))
    }

    private fun resolve(
        slots: Map<ModelSlotKind, List<ModelId>>,
        snapshot: CatalogSnapshot,
    ): Pair<Map<ModelSlotKind, SlotViewOut>, SlotView> {
        val resolved =
            ModelSlotKind.entries.associateWith {
                SlotResolution.resolve(it, slots[it].orEmpty(), snapshot.models)
            }
        val views =
            resolved.mapValues { (_, slot) ->
                SlotViewOut(
                    SlotViewState.valueOf(slot.state.name),
                    slot.currentModelId?.value,
                    slot.chain.map {
                        ChainModelView(
                            it.modelId.value,
                            snapshot.models[it.modelId]?.name,
                            AvailabilityView.valueOf(it.availability.name),
                        )
                    },
                )
            }
        return views to resolved.getValue(ModelSlotKind.TEXT)
    }

    private fun price(
        text: SlotView,
        snapshot: CatalogSnapshot,
    ): PriceView {
        val estimate = PriceEstimate.of(text.currentModelId?.let { snapshot.models[it] })
        return PriceView(PriceViewState.valueOf(estimate.state.name), estimate.amount)
    }

    private fun toView(model: CatalogModel) =
        CatalogModelView(
            modelId = model.modelId.value,
            name = model.name,
            provider = model.provider,
            takes = model.takes.mapTo(linkedSetOf()) { it.name },
            produces = model.produces.mapTo(linkedSetOf()) { it.name },
            slots = model.slots.mapTo(linkedSetOf()) { ModelSlotKind.valueOf(it.name) },
            inputPerMtok = model.inputPerMtok,
            outputPerMtok = model.outputPerMtok,
            perImage = model.perImage,
            contextLength = model.contextLength,
        )
}
