package telex.messaging.internal.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Installation-wide cap on Linked Accounts per Owner (AC-115). Read on every check, so lowering it never unlinks
 * anything: it only refuses the next link.
 */
@Component
class AccountLimit(
    @Value("\${telex.telegram.max-accounts-per-owner:3}") val maxPerOwner: Int,
) {
    init {
        require(maxPerOwner >= 1) { "telex.telegram.max-accounts-per-owner must be at least 1" }
    }
}
