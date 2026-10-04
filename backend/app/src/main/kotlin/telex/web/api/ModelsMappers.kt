package telex.web.api

import telex.agents.CatalogModelView
import telex.agents.ChainModelView
import telex.agents.DraftView
import telex.agents.ModelCatalogView
import telex.agents.ModelProfileListView
import telex.agents.ModelProfileView
import telex.agents.ModelSlotKind
import telex.agents.PriceView
import telex.agents.SlotView
import java.math.BigDecimal

private fun Enum<*>.wire() = name.lowercase().replace('_', '-')

private fun BigDecimal.usd() = toPlainString()

private fun CatalogModelView.toBody() =
    CatalogModelBody(
        modelId,
        name,
        provider,
        takes.map { it.lowercase() },
        produces.map { it.lowercase() },
        slots.map { it.wire },
        inputPerMtok?.usd(),
        outputPerMtok?.usd(),
        perImage?.usd(),
        contextLength,
    )

fun ModelCatalogView.toBody() =
    ModelCatalogBody(state.wire(), lastRefreshedAt, lastFailedAt, models.map { it.toBody() })

private fun ChainModelView.toBody() = ChainModelBody(modelId, name, availability.wire())

private fun SlotView.toBody() = SlotBody(state.wire(), currentModelId, chain.map { it.toBody() })

private fun Map<ModelSlotKind, SlotView>.toBody() =
    ProfileSlotsBody(
        getValue(ModelSlotKind.TEXT).toBody(),
        getValue(ModelSlotKind.VISION).toBody(),
        getValue(ModelSlotKind.IMAGE).toBody(),
    )

private fun PriceView.toBody() = PriceBody(state.wire(), amount?.toPlainString())

fun ModelProfileView.toBody() = ModelProfileBody(ref.toBody(), name, slots.toBody(), price.toBody(), choosable)

fun ModelProfileListView.toBody() =
    ModelProfileListBody(aiConfigured, defaultProfile.toBody(), customProfileLimit, items.map { it.toBody() })

fun DraftView.toBody() = ModelProfileDraftBody(name, duplicatedFrom?.toBody(), slots.toBody(), price.toBody())
