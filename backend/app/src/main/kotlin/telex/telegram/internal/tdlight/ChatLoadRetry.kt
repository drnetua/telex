package telex.telegram.internal.tdlight

import telex.telegram.tdlib.TdlibResponse
import java.time.Duration

/** How long a chat-list load that stopped waits before it is retried while the session stays connected. */
internal object ChatLoadRetry {
    private const val TOO_MANY_REQUESTS = 429
    private const val BASE_SECONDS = 5L
    private const val MAX_DOUBLINGS = 6
    private const val MAX_SECONDS = 300L
    private val RETRY_AFTER = Regex("retry after (\\d+)")

    /**
     * Telegram's `retry after N` when the load was flooded, else a delay that doubles from 5 s per failure; both are
     * kept within 1–300 s, so a load that keeps failing is never retried in a tight loop.
     */
    fun delay(
        failure: TdlibResponse.Failure?,
        failures: Int,
    ): Duration {
        val retryAfter =
            failure
                ?.takeIf { it.code == TOO_MANY_REQUESTS }
                ?.let { RETRY_AFTER.find(it.message) }
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        val seconds = retryAfter ?: (BASE_SECONDS shl (failures - 1).coerceIn(0, MAX_DOUBLINGS))
        return Duration.ofSeconds(seconds.coerceIn(1, MAX_SECONDS))
    }
}
