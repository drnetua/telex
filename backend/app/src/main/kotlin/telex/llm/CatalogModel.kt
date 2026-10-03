package telex.llm

import java.math.BigDecimal

data class CatalogModel(
    val modelId: ModelId,
    val name: String,
    val provider: String,
    val takes: Set<Modality>,
    val produces: Set<Modality>,
    val inputPerMtok: BigDecimal?,
    val outputPerMtok: BigDecimal?,
    val perImage: BigDecimal?,
    val contextLength: Int?,
) {
    fun fits(slot: ModelSlotKind): Boolean =
        when (slot) {
            ModelSlotKind.TEXT -> Modality.TEXT in takes && Modality.TEXT in produces
            ModelSlotKind.VISION -> Modality.IMAGE in takes && Modality.TEXT in produces
            ModelSlotKind.IMAGE -> Modality.IMAGE in produces
        }

    val slots: Set<ModelSlotKind> get() = ModelSlotKind.entries.filterTo(linkedSetOf()) { fits(it) }
}
