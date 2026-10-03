package telex.identity

/** The Owner's colour theme preference; [wire] is the lowercase API value. */
enum class Theme(
    val wire: String,
) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromWire(value: String): Theme? = entries.firstOrNull { it.wire == value }
    }
}
