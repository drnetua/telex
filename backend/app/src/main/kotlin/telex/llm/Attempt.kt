package telex.llm

enum class AttemptOutcome(
    val movesOn: Boolean,
) {
    ANSWERED(false),
    MISSING(true),
    UNAVAILABLE(true),
    PROVIDER_ERROR(true),
    RATE_LIMITED(true),
    TIMEOUT(true),
    TOO_LARGE(true),
    CONTENT_REFUSED(false),
    INVALID_REQUEST(false),
}

data class Attempt(
    val modelId: ModelId,
    val outcome: AttemptOutcome,
)
