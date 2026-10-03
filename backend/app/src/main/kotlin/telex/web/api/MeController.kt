package telex.web.api

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import telex.identity.OwnerPreferences
import telex.identity.OwnerProfiles
import telex.identity.Preferences
import telex.identity.SignedInOwner
import telex.identity.Theme
import telex.shared.DomainProblem
import telex.shared.FieldProblem
import java.util.UUID

data class MeBody(
    val ownerId: UUID,
    val email: String,
    val linkedAccountCount: Int,
    val theme: String,
    val timeZone: String?,
    val timeZoneIsFallback: Boolean,
)

data class PreferencesBody(
    val theme: String,
    val timeZone: String?,
    val timeZoneIsFallback: Boolean,
)

data class DetectedTimeZoneBody(
    val timeZone: String?,
)

/** `Preferences.timeZone` is never null on the wire: an Owner with none saved yet is shown UTC as the fallback. */
private fun Preferences.toBody() =
    if (timeZone == null) {
        PreferencesBody(theme.wire, "UTC", true)
    } else {
        PreferencesBody(theme.wire, timeZone, timeZoneIsFallback)
    }

@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val profiles: OwnerProfiles,
    private val preferences: OwnerPreferences,
) {
    @GetMapping
    fun me(
        @AuthenticationPrincipal principal: SignedInOwner,
    ): MeBody {
        val me =
            profiles.me(principal.ownerId)
                ?: throw DomainProblem(HttpStatus.NOT_FOUND, "not-found", "Not found.")
        return MeBody(
            me.ownerId.value,
            me.email,
            me.linkedAccountCount,
            me.theme.wire,
            me.timeZone,
            me.timeZoneIsFallback,
        )
    }

    /**
     * Changes the session Owner's theme and/or timezone. A property missing from [body] stays; a present `null`
     * is a value (refused for the timezone). Nothing is saved unless every given value is accepted.
     */
    @PatchMapping("/preferences")
    fun change(
        @AuthenticationPrincipal principal: SignedInOwner,
        @RequestBody body: Map<String, Any?>,
    ): PreferencesBody {
        val owner = principal.ownerId
        val theme =
            if ("theme" in body) {
                (body["theme"] as? String)?.let(Theme::fromWire) ?: throw unknownTheme()
            } else {
                null
            }
        if ("timeZone" in body) preferences.changeTimeZone(owner, body["timeZone"]?.toString())
        if (theme != null) preferences.changeTheme(owner, theme)
        return checkNotNull(preferences.of(owner)) { "owner vanished" }.toBody()
    }

    @PostMapping("/preferences/detected-time-zone")
    fun saveDetectedTimeZone(
        @AuthenticationPrincipal principal: SignedInOwner,
        @RequestBody body: DetectedTimeZoneBody,
    ): PreferencesBody = preferences.saveDetectedTimeZone(principal.ownerId, body.timeZone).toBody()

    private fun unknownTheme(): DomainProblem {
        val message = "Choose light, dark or system."
        return DomainProblem(
            HttpStatus.BAD_REQUEST,
            "validation-failed",
            message,
            listOf(FieldProblem("theme", "unknown-theme", message)),
        )
    }
}
