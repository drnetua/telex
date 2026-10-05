package telex.telegram.internal.tdlight

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.telegram.tdlib.TdlibResponse
import java.time.Duration

class ChatLoadRetryTest {
    private fun seconds(
        failure: TdlibResponse.Failure?,
        failures: Int,
    ) = ChatLoadRetry.delay(failure, failures).seconds

    @Test
    fun `a load that keeps failing backs off from 5 s, doubling up to 300 s (AC-116)`() {
        assertThat((1..9).map { seconds(TdlibResponse.Failure(500, "Internal"), it) })
            .containsExactly(5L, 10L, 20L, 40L, 80L, 160L, 300L, 300L, 300L)
    }

    @Test
    fun `a load that stopped without a Telegram answer backs off the same way (AC-116)`() {
        assertThat(ChatLoadRetry.delay(null, 1)).isEqualTo(Duration.ofSeconds(5))
        assertThat(ChatLoadRetry.delay(null, 2)).isEqualTo(Duration.ofSeconds(10))
    }

    @Test
    fun `a flooded load waits Telegram's retry-after, kept within 1 to 300 s (AC-116, AC-121)`() {
        assertThat(seconds(TdlibResponse.Failure(429, "Too Many Requests: retry after 7"), 3)).isEqualTo(7L)
        assertThat(seconds(TdlibResponse.Failure(429, "Too Many Requests: retry after 3600"), 1)).isEqualTo(300L)
        assertThat(seconds(TdlibResponse.Failure(429, "Too Many Requests: retry after 0"), 1)).isEqualTo(1L)
    }

    @Test
    fun `only a 429 carries a retry-after, and a 429 without one backs off (AC-116)`() {
        assertThat(seconds(TdlibResponse.Failure(400, "retry after 7"), 2)).isEqualTo(10L)
        assertThat(seconds(TdlibResponse.Failure(429, "Too Many Requests"), 1)).isEqualTo(5L)
    }
}
