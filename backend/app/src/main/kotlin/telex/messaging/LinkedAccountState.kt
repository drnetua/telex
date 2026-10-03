package telex.messaging

/** The state of a Linked Account; [wire] is the `linked_account.state` value and the API value. */
enum class LinkedAccountState(
    val wire: String,
) {
    CONNECTED("connected"),
    RECONNECTING("reconnecting"),
    SESSION_LOST("session_lost"),
    ;

    companion object {
        fun fromWire(value: String): LinkedAccountState =
            entries.firstOrNull { it.wire == value } ?: error("Unknown linked account state: $value")
    }
}

/** A phone number shown masked: the country code and the last two digits only (the full number is never stored). */
data class MaskedPhone(
    val countryCode: String,
    val lastDigits: String,
) {
    init {
        require(countryCode.matches(Regex("[0-9]{1,3}"))) { "country code must be 1-3 digits" }
        require(lastDigits.matches(Regex("[0-9]{2}"))) { "last digits must be 2 digits" }
    }

    override fun toString(): String = "+$countryCode ••• ••$lastDigits"
}
