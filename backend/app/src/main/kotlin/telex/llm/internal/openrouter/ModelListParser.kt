package telex.llm.internal.openrouter

import org.slf4j.LoggerFactory
import telex.llm.CatalogModel
import telex.llm.Modality
import telex.llm.ModelId
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

data class SkippedModel(
    val id: String?,
    val reason: String,
)

data class ParsedCatalog(
    val models: List<CatalogModel>,
    val skipped: List<SkippedModel>,
)

/**
 * Pure, defensive mapping of OpenRouter's `/models` body to catalog models (sad §11).
 *
 * Price decision: OpenRouter's `pricing.image_output` is a per-output-image-token price, not per image,
 * so no per-image price can be identified from the response and `perImage` stays null.
 */
object ModelListParser {
    private const val MAX_ID_LENGTH = 200
    private const val MAX_NAME_LENGTH = 200
    private const val MAX_PROVIDER_LENGTH = 100
    private const val PER_MILLION = 6
    private val MAX_PRICE = BigDecimal.TEN.pow(8)
    private val log = LoggerFactory.getLogger(ModelListParser::class.java)
    private val mapper = JsonMapper.builder().build()

    fun parse(json: String): ParsedCatalog {
        val models = mutableListOf<CatalogModel>()
        val skipped = mutableListOf<SkippedModel>()
        val seen = hashSetOf<String>()
        for (node in mapper.readTree(json).path("data")) {
            when (val r = parseOne(node)) {
                is CatalogModel -> {
                    if (seen.add(r.modelId.value)) {
                        models += r
                    } else {
                        skipped += SkippedModel(r.modelId.value, "duplicate id").also { logSkipped(it) }
                    }
                }

                is SkippedModel -> {
                    skipped += r.also { logSkipped(it) }
                }
            }
        }
        return ParsedCatalog(models, skipped)
    }

    private fun logSkipped(it: SkippedModel) = log.warn("Skipped model {}: {}", it.id, it.reason)

    private fun parseOne(node: JsonNode): Any {
        val id = node.path("id").takeIf { it.isString }?.asString()
        val takes = modalities(node.path("architecture").path("input_modalities"))
        val produces = modalities(node.path("architecture").path("output_modalities"), ignoreUnused = false)
        val name =
            node
                .path("name")
                .takeIf { it.isString }
                ?.asString()
                ?.ifBlank { null } ?: id
        val pricing = node.path("pricing")
        val inputPerMtok = perMtok(pricing.path("prompt"))
        val outputPerMtok = perMtok(pricing.path("completion"))
        val reason =
            when {
                id.isNullOrBlank() -> {
                    "missing id"
                }

                id.length > MAX_ID_LENGTH -> {
                    "id longer than $MAX_ID_LENGTH characters"
                }

                name!!.length > MAX_NAME_LENGTH -> {
                    "name longer than $MAX_NAME_LENGTH characters"
                }

                id.substringBefore('/').length > MAX_PROVIDER_LENGTH -> {
                    "provider longer than $MAX_PROVIDER_LENGTH characters"
                }

                !fitsColumn(inputPerMtok) || !fitsColumn(outputPerMtok) -> {
                    "price beyond the storable range"
                }

                takes == null || produces == null -> {
                    "unknown or missing modalities"
                }

                else -> {
                    null
                }
            }
        if (reason != null) return SkippedModel(id, reason)
        val model =
            CatalogModel(
                modelId = ModelId(id!!),
                name = name!!,
                provider = id.substringBefore('/'),
                takes = takes!!,
                produces = produces!!,
                inputPerMtok = inputPerMtok,
                outputPerMtok = outputPerMtok,
                perImage = null,
                contextLength =
                    node
                        .path("context_length")
                        .takeIf { it.isIntegralNumber }
                        ?.asInt()
                        ?.takeIf { it > 0 },
            )
        return if (model.slots.isEmpty()) SkippedModel(id, "fits no slot") else model
    }

    /** Null when absent or a name is unknown; unused input kinds (audio, files) pass, unusable outputs do not. */
    private fun modalities(
        node: JsonNode,
        ignoreUnused: Boolean = true,
    ): Set<Modality>? {
        val names = if (node.isArray) node.values().map { it.asString() } else emptyList()
        val valid = names.isNotEmpty() && names.all { it in KNOWN || (ignoreUnused && it in IGNORED) }
        return if (valid) names.mapNotNullTo(linkedSetOf()) { KNOWN[it] } else null
    }

    /** `NUMERIC(14,6)` holds below 10^8; a larger price would fail the whole refresh's insert. */
    private fun fitsColumn(price: BigDecimal?) = price == null || price < MAX_PRICE

    private fun perMtok(node: JsonNode): BigDecimal? =
        node
            .takeIf { it.isString }
            ?.asString()
            ?.toBigDecimalOrNull()
            ?.takeIf { it.signum() >= 0 }
            ?.movePointRight(PER_MILLION)

    private val KNOWN = mapOf("text" to Modality.TEXT, "image" to Modality.IMAGE)
    private val IGNORED = setOf("audio", "file", "video", "embeddings")
}
