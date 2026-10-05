package telex.messaging.internal.account

import org.springframework.stereotype.Component
import telex.identity.OwnerId
import telex.shared.StatusConditionSource
import java.util.UUID

/** AC-122: the shell's Status Banner reports `account-disconnected` while the Owner has a Session lost account. */
@Component
class SessionLostConditions(
    private val rows: LinkedAccountRows,
) : StatusConditionSource {
    override fun activeConditions(ownerId: UUID): Set<String> =
        if (rows.hasSessionLost(OwnerId(ownerId))) setOf(ACCOUNT_DISCONNECTED) else emptySet()

    private companion object {
        const val ACCOUNT_DISCONNECTED = "account-disconnected"
    }
}
