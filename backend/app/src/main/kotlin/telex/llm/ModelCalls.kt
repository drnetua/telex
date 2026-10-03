package telex.llm

data class ChatMessage(
    val role: String,
    val content: String,
)

sealed interface SlotRequest {
    data class Text(
        val messages: List<ChatMessage>,
    ) : SlotRequest

    data class Vision(
        val messages: List<ChatMessage>,
        val images: List<ImageInput>,
    ) : SlotRequest

    data class Image(
        val prompt: String,
    ) : SlotRequest
}

/** An image passed to a vision call: raw bytes plus their media type (e.g. image/png); never stored or logged. */
class ImageInput(
    val bytes: ByteArray,
    val mediaType: String,
)

sealed interface SlotAnswer {
    data class Text(
        val text: String,
    ) : SlotAnswer

    data class Image(
        val bytes: ByteArray,
        val mediaType: String,
    ) : SlotAnswer
}

enum class ModelCallFailure { NO_MODEL_ANSWERED, AI_NOT_CONFIGURED }

sealed interface ModelCallResult {
    val attempts: List<Attempt>

    data class Answered(
        val answer: SlotAnswer,
        val answeredBy: ModelId,
        val fallback: Boolean,
        override val attempts: List<Attempt>,
    ) : ModelCallResult

    data class Failed(
        val reason: ModelCallFailure,
        override val attempts: List<Attempt>,
    ) : ModelCallResult
}

interface ModelCalls {
    /** One attempt per chain model, in order, falling back inside the call. Never throws for a model failure. */
    fun call(
        chain: List<ModelId>,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ModelCallResult
}
