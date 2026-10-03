package telex.identity

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import telex.identity.internal.owner.Owners
import telex.shared.DomainProblem
import telex.shared.FieldProblem

/** The Owner's saved preferences; [timeZone] is null until the first save, [timeZoneIsFallback] marks UTC fallback. */
data class Preferences(
    val theme: Theme,
    val timeZone: String?,
    val timeZoneIsFallback: Boolean,
)

/** Theme and timezone rules: never-empty timezone, list-only zones, first detected save only while unset. */
@Service
class OwnerPreferences(
    private val owners: Owners,
) {
    fun of(ownerId: OwnerId): Preferences? = owners.preferencesOf(ownerId)

    fun timeZoneOf(ownerId: OwnerId): String? = owners.preferencesOf(ownerId)?.timeZone

    fun changeTheme(
        ownerId: OwnerId,
        theme: Theme,
    ) {
        owners.updateTheme(ownerId, theme.wire)
    }

    /** Picks a zone from the list; empty or unknown values are refused and the saved zone is kept. */
    fun changeTimeZone(
        ownerId: OwnerId,
        zone: String?,
    ): Preferences {
        if (zone.isNullOrBlank()) throw refused("time-zone-required", "Choose a timezone.")
        if (!TimeZones.isKnown(zone)) throw refused("unknown-time-zone", "Choose a timezone from the list.")
        owners.updateTimeZone(ownerId, zone)
        return checkNotNull(owners.preferencesOf(ownerId)) { "owner vanished" }
    }

    /** Saves the device zone only while none is saved; unknown or missing zones save UTC as the fallback. */
    fun saveDetectedTimeZone(
        ownerId: OwnerId,
        zone: String?,
    ): Preferences {
        val known = zone?.takeIf { TimeZones.isKnown(it) }
        owners.saveTimeZoneIfUnset(ownerId, known ?: "UTC", known == null)
        return checkNotNull(owners.preferencesOf(ownerId)) { "owner vanished" }
    }

    private fun refused(
        code: String,
        message: String,
    ) = DomainProblem(
        HttpStatus.BAD_REQUEST,
        "validation-failed",
        message,
        listOf(FieldProblem("timeZone", code, message)),
    )
}
