package telex.inbox

import java.util.UUID

/** Implemented by each module whose items can wait for an Owner; must filter on that Owner's id only. */
fun interface InboxSource {
    fun countWaiting(ownerId: UUID): Int
}
