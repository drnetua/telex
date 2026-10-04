package telex.agents

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue

enum class SystemProfileKey(
    @get:JsonValue val wire: String,
    val displayName: String,
) {
    FAST("fast", "Fast and cheap"),
    BALANCED("balanced", "Balanced"),
    CAREFUL("careful", "Careful"),
}

/** Tagged on the wire (`{"kind":"system","key":...}`) so events carrying it survive the publication registry. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = ProfileRef.System::class, name = "system"),
    JsonSubTypes.Type(value = ProfileRef.Custom::class, name = "custom"),
)
sealed interface ProfileRef {
    data class System(
        val key: SystemProfileKey,
    ) : ProfileRef

    data class Custom(
        val id: ModelProfileId,
    ) : ProfileRef
}
