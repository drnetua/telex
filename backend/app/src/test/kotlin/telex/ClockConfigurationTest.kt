package telex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ClockConfigurationTest {
    @Test
    fun `the clock bean is UTC`() {
        assertThat(ClockConfiguration().clock().zone).isEqualTo(ZoneOffset.UTC)
    }
}
