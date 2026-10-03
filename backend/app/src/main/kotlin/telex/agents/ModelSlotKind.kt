package telex.agents

import com.fasterxml.jackson.annotation.JsonValue

enum class ModelSlotKind(
    @get:JsonValue val wire: String,
) {
    TEXT("text"),
    VISION("vision"),
    IMAGE("image"),
}
