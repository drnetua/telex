package telex.agents

enum class ModelSlotKind(
    val wire: String,
) {
    TEXT("text"),
    VISION("vision"),
    IMAGE("image"),
}
