package telex.identity

import org.springframework.stereotype.Service
import telex.identity.internal.owner.Owners

data class Me(
    val ownerId: OwnerId,
    val email: String,
    val theme: Theme,
    val timeZone: String?,
    val timeZoneIsFallback: Boolean,
)

/** "Who am I". The Linked Account count is not here: `messaging` depends on `identity`, so `web` adds it. */
@Service
class OwnerProfiles(
    private val owners: Owners,
) {
    fun me(ownerId: OwnerId): Me? =
        owners.emailAndPreferencesOf(ownerId)?.let { (email, prefs) ->
            Me(ownerId, email, prefs.theme, prefs.timeZone, prefs.timeZoneIsFallback)
        }
}
