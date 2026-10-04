package telex.web.api

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import telex.agents.CatalogModelView
import telex.agents.ChainModelView
import telex.agents.DraftView
import telex.agents.ModelCatalogView
import telex.agents.ModelProfileId
import telex.agents.ModelProfileListView
import telex.agents.ModelProfileView
import telex.agents.ModelSlotKind
import telex.agents.PriceView
import telex.agents.ProfileDeletion
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
) : StrictBody() {
    // `ProfileRef` is a strict oneOf: `{kind: system, key}` or `{kind: custom, id}`; a mixed shape is unreadable (400).
    init {
        val exactlyOneVariant =
            when (kind) {
                "system" -> key != null && id == null
                "custom" -> id != null && key == null
                else -> false
            }
        require(exactlyOneVariant) { "A profile reference is either {kind: system, key} or {kind: custom, id}" }
    }
}

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

/** Request schemas are `additionalProperties: false`: an unknown property makes the body unreadable (400). */
abstract class StrictBody {
    @JsonAnySetter
    fun unknownProperty(
        name: String,
        value: Any?,
    ): Unit = throw IllegalArgumentException("Unknown property '$name' (${value?.javaClass?.simpleName})")
}

/** Request bounds of `openapi.yaml`; the slot cap is generous so the domain still reports `slot-full` (AC-217). */
private const val MAX_NAME_INPUT = 200
private const val MAX_MODEL_ID = 200
private const val MAX_SLOT_INPUT = 10

/** A missing slot is an empty slot; the rules (and their codes) live in `agents`. */
data class ChainInputBody(
    @field:Size(max = MAX_SLOT_INPUT) val text: List<
        @Size(max = MAX_MODEL_ID)
        String,
    > = emptyList(),
    @field:Size(max = MAX_SLOT_INPUT) val vision: List<
        @Size(max = MAX_MODEL_ID)
        String,
    > = emptyList(),
    @field:Size(max = MAX_SLOT_INPUT) val image: List<
        @Size(max = MAX_MODEL_ID)
        String,
    > = emptyList(),
) : StrictBody() {
    fun toSlots(): Map<ModelSlotKind, List<String>> =
        mapOf(ModelSlotKind.TEXT to text, ModelSlotKind.VISION to vision, ModelSlotKind.IMAGE to image)
}

data class ProfileWriteBody(
    @field:Size(max = MAX_NAME_INPUT) val name: String = "",
    @field:Valid val slots: ChainInputBody = ChainInputBody(),
    val duplicatedFrom: ProfileRefBody? = null,
) : StrictBody()

data class DefaultProfileChoiceBody(
    @field:NotNull @field:Valid val profile: ProfileRefBody?,
) : StrictBody()

data class DefaultProfileBody(
    val profile: ProfileRefBody,
)

data class ModelProfileDeletionBody(
    val defaultProfile: ProfileRefBody,
    val defaultReset: Boolean,
)

fun ProfileDeletion.toBody() = ModelProfileDeletionBody(defaultProfile.toBody(), defaultReset)

/** A profile reference in a body: a system key or a custom id; anything unresolvable is simply not found. */
fun ProfileRefBody.toRef(): ProfileRef =
    when (kind) {
        "system" -> parseProfileKey(key.orEmpty())
        "custom" -> parseProfileKey(id?.toString().orEmpty())
        else -> throw ProfileNotFound()
    }
