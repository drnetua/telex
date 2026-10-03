package telex.identity

import java.time.ZoneId

/** The known timezone list: IANA `Area/City` ids from the JDK plus `UTC`. Computed once, sorted by id. */
object TimeZones {
    private const val UTC = "UTC"
    private const val MAX_LENGTH = 64
    private val areas =
        setOf(
            "Africa",
            "America",
            "Antarctica",
            "Arctic",
            "Asia",
            "Atlantic",
            "Australia",
            "Europe",
            "Indian",
            "Pacific",
        )

    val ids: List<String> =
        (
            ZoneId.getAvailableZoneIds().filter {
                it.substringBefore(
                    '/',
                ) in areas && '/' in it && it.length <= MAX_LENGTH
            } +
                UTC
        ).distinct()
            .sorted()

    private val known: Set<String> = ids.toHashSet()

    fun isKnown(id: String): Boolean = id in known
}
