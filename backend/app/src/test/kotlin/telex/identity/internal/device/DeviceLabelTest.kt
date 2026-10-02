@file:Suppress("MaxLineLength")

package telex.identity.internal.device

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DeviceLabelTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36|Chrome on Mac|computer",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15|Safari on Mac|computer",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0|Edge on Windows|computer",
            "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0|Firefox on Linux|computer",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1|Safari on iPhone|phone",
            "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1|Safari on iPad|tablet",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36|Chrome on Android|phone",
        ],
    )
    fun `maps a user agent to a label and a device type`(
        userAgent: String,
        label: String,
        type: String,
    ) {
        val device = DeviceLabel.from(userAgent)

        assertThat(device.label).isEqualTo(label)
        assertThat(device.deviceType).isEqualTo(type)
    }

    @Test
    fun `unknown or missing user agents get the neutral label`() {
        for (ua in listOf(null, "", "curl/8.4.0")) {
            val device = DeviceLabel.from(ua)
            assertThat(device.label).isEqualTo("Unknown browser")
            assertThat(device.deviceType).isEqualTo("unknown")
        }
    }

    @Test
    fun `a label is at most 100 characters`() {
        assertThat(DeviceLabel.from("x".repeat(500) + " Firefox/1 Linux").label.length).isLessThanOrEqualTo(100)
    }
}
