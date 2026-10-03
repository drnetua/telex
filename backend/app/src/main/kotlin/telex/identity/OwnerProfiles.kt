package telex.identity

import org.springframework.stereotype.Service
import telex.identity.internal.owner.Owners

data class Me(
    val ownerId: OwnerId,
    val email: String,
    val linkedAccountCount: Int,
    val theme: Theme,
    val timeZone: String?,
    val timeZoneIsFallback: Boolean,
)

/** "Who am I". No Linked Account store exists before E02, so the count is zero. */
@Service
class OwnerProfiles(
    private val owners: Owners,
) {
    fun me(ownerId: OwnerId): Me? =
        owners.emailAndPreferencesOf(ownerId)?.let { (email, prefs) ->
            Me(ownerId, email, 0, prefs.theme, prefs.timeZone, prefs.timeZoneIsFallback)
        }
}
