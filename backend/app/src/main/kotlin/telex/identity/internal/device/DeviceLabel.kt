package telex.identity.internal.device

/** "<Browser> on <Device>" and a device type (`phone`, `tablet`, `computer`, `unknown`) from a User-Agent. */
data class DeviceLabel(
    val label: String,
    val deviceType: String,
) {
    companion object {
        private val UNKNOWN = DeviceLabel("Unknown browser", "unknown")

        fun from(userAgent: String?): DeviceLabel {
            val ua = userAgent.orEmpty()
            val browser = browser(ua)
            val platform = platform(ua)
            return if (browser == null || platform == null) {
                UNKNOWN
            } else {
                DeviceLabel("$browser on ${platform.first}", platform.second)
            }
        }

        private fun browser(ua: String): String? =
            when {
                ua.contains("Edg/") || ua.contains("Edge/") -> "Edge"
                ua.contains("Firefox/") || ua.contains("FxiOS/") -> "Firefox"
                ua.contains("Chrome/") || ua.contains("CriOS/") -> "Chrome"
                ua.contains("Safari/") -> "Safari"
                else -> null
            }

        private fun platform(ua: String): Pair<String, String>? =
            when {
                ua.contains("iPhone") -> "iPhone" to "phone"
                ua.contains("iPad") -> "iPad" to "tablet"
                ua.contains("Android") -> "Android" to if (ua.contains("Mobile")) "phone" else "tablet"
                ua.contains("Windows") -> "Windows" to "computer"
                ua.contains("Macintosh") -> "Mac" to "computer"
                ua.contains("Linux") || ua.contains("X11") -> "Linux" to "computer"
                else -> null
            }
    }
}
