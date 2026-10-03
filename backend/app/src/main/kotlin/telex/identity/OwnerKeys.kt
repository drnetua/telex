package telex.identity

import org.springframework.stereotype.Service
import telex.identity.internal.key.AesGcm
import telex.identity.internal.key.MasterKey
import telex.identity.internal.key.OwnerKeyRows
import java.security.SecureRandom
import java.time.Clock

/**
 * Envelope encryption (ADR-0003): a random per-Owner key stored sealed under the installation master key.
 * The Owner key is never handed out; callers seal and open values with an `aad` that binds them to their
 * subject (the Linked Account), so a sealed value cannot be moved to another one.
 */
@Service
class OwnerKeys(
    private val masterKey: MasterKey,
    private val rows: OwnerKeyRows,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    /** True once a master key is configured; false on an installation that cannot link accounts yet (AC-119). */
    fun ready(): Boolean = masterKey.configured

    /** True when this start deleted every Owner key (`TELEX_MASTER_KEY_RESET`), so accounts are "Session lost". */
    @Volatile
    var resetPerformedAtStartup: Boolean = false
        internal set

    fun seal(
        ownerId: OwnerId,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = AesGcm.seal(ownerKey(ownerId, create = true), plaintext, aad)

    fun open(
        ownerId: OwnerId,
        sealed: ByteArray,
        aad: ByteArray,
    ): ByteArray = AesGcm.open(ownerKey(ownerId, create = false), sealed, aad)

    private fun ownerKey(
        ownerId: OwnerId,
        create: Boolean,
    ): ByteArray {
        val master = masterKey.bytes()
        val aad = aadOf(ownerId)
        var sealed = rows.find(ownerId)
        if (sealed == null) {
            check(create) { "Owner has no key" }
            val fresh = ByteArray(OWNER_KEY_BYTES).also(random::nextBytes)
            rows.insertIfAbsent(ownerId, AesGcm.seal(master, fresh, aad), clock.instant())
            sealed = checkNotNull(rows.find(ownerId))
        }
        return AesGcm.open(master, sealed, aad)
    }

    internal fun aadOf(ownerId: OwnerId): ByteArray = ownerId.value.toString().toByteArray(Charsets.UTF_8)

    private companion object {
        const val OWNER_KEY_BYTES = 32
    }
}
