package telex.agents

enum class SystemProfileKey(
    val wire: String,
    val displayName: String,
) {
    FAST("fast", "Fast"),
    BALANCED("balanced", "Balanced"),
    CAREFUL("careful", "Careful"),
}

sealed interface ProfileRef {
    data class System(
        val key: SystemProfileKey,
    ) : ProfileRef

    data class Custom(
        val id: ModelProfileId,
    ) : ProfileRef
}
