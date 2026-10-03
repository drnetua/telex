package telex.web.api

import com.fasterxml.jackson.annotation.JsonInclude
import telex.agents.CatalogModelView
import telex.agents.ChainModelView
import telex.agents.DraftView
import telex.agents.ModelCatalogView
import telex.agents.ModelProfileId
import telex.agents.ModelProfileListView
import telex.agents.ModelProfileView
import telex.agents.ModelSlotKind
import telex.agents.PriceView
import telex.agents.ProfileNotFound
import telex.agents.ProfileRef
import telex.agents.SlotView
import telex.agents.SystemProfileKey
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Wire shapes of `model-profiles/contracts/openapi.yaml`; enum values are kebab-case, money is a string. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProfileRefBody(
    val kind: String,
    val key: String? = null,
    val id: UUID? = null,
)

data class CatalogModelBody(
    val modelId: String,
    val name: String,
    val provider: String,
    val takes: List<String>,
    val produces: List<String>,
    val slots: List<String>,
    val inputPricePerMillionTokens: String?,
    val outputPricePerMillionTokens: String?,
    val pricePerImage: String?,
    val contextLength: Int?,
)

data class ModelCatalogBody(
    val state: String,
    val lastRefreshedAt: Instant?,
    val lastFailedAt: Instant?,
    val models: List<CatalogModelBody>,
)

data class ChainModelBody(
    val modelId: String,
    val name: String?,
    val availability: String,
)

data class SlotBody(
    val state: String,
    val currentModelId: String?,
    val chain: List<ChainModelBody>,
)

data class ProfileSlotsBody(
    val text: SlotBody,
    val vision: SlotBody,
    val image: SlotBody,
)

data class PriceBody(
    val state: String,
    val amount: String?,
)

data class ModelProfileBody(
    val ref: ProfileRefBody,
    val name: String,
    val slots: ProfileSlotsBody,
    val pricePer100Runs: PriceBody,
    val choosable: Boolean,
)

data class ModelProfileListBody(
    val aiConfigured: Boolean,
    val defaultProfile: ProfileRefBody,
    val customProfileLimit: Int,
    val items: List<ModelProfileBody>,
)

data class ModelProfileDraftBody(
    val name: String,
    val duplicatedFrom: ProfileRefBody?,
    val slots: ProfileSlotsBody,
    val pricePer100Runs: PriceBody,
)

/** A path segment or query value: a system key, or a custom profile uuid; anything else is simply not found. */
fun parseProfileKey(raw: String): ProfileRef {
    SystemProfileKey.entries.firstOrNull { it.wire == raw }?.let { return ProfileRef.System(it) }
    val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw ProfileNotFound()
    return ProfileRef.Custom(ModelProfileId(id))
}

fun ProfileRef.toBody(): ProfileRefBody =
    when (this) {
        is ProfileRef.System -> ProfileRefBody("system", key = key.wire)
        is ProfileRef.Custom -> ProfileRefBody("custom", id = id.value)
    }
