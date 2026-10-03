package telex.inbox

import org.springframework.stereotype.Component
import java.util.UUID

/** Sums every [InboxSource] for one Owner; 0 when there are none. */
@Component
class Inbox(
    private val sources: List<InboxSource>,
) {
    fun countWaiting(ownerId: UUID): Int = sources.sumOf { it.countWaiting(ownerId) }
}
