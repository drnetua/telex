package telex.identity.internal.key

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import telex.identity.OwnerKeys
import java.security.GeneralSecurityException

/**
 * Startup master-key check (ADR-0003): opens any one stored Owner key, and refuses to start when the configured key
 * is missing or wrong. With `TELEX_MASTER_KEY_RESET=true` it deletes every Owner key instead.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MasterKeyCheck(
    private val masterKey: MasterKey,
    @Value($$"${telex.master-key-reset:false}") private val reset: Boolean,
    private val rows: OwnerKeyRows,
    private val ownerKeys: OwnerKeys,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (reset) {
            rows.deleteAll()
            ownerKeys.resetPerformedAtStartup = true
            return
        }
        val (ownerId, sealed) = rows.any() ?: return
        check(masterKey.configured) { REFUSAL }
        try {
            AesGcm.open(masterKey.bytes(), sealed, ownerKeys.aadOf(ownerId))
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(REFUSAL, e)
        }
    }

    private companion object {
        const val REFUSAL =
            "TELEX_MASTER_KEY is missing or does not match the one this installation's Owner keys were sealed " +
                "with; set the original key, or set TELEX_MASTER_KEY_RESET=true to discard every Owner key"
    }
}
