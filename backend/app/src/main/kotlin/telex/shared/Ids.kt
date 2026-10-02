package telex.shared

import java.security.SecureRandom
import java.util.UUID

/**
 * An aggregate id, typed per aggregate: `@JvmInline value class OwnerId(override val value: UUID) : TypedId`.
 * Values come from [Uuid7] and are assigned before insert.
 */
interface TypedId {
    val value: UUID
}

/**
 * UUIDv7 (RFC 9562): 48-bit Unix epoch milliseconds, version 7, a 12-bit counter that keeps ids
 * generated within the same millisecond ordered, variant `10`, 62 random bits.
 */
object Uuid7 {
    private const val VERSION = 7
    private const val TIMESTAMP_SHIFT = 16
    private const val VERSION_BITS = 0x7000L
    private const val COUNTER_MAX = 0xFFF
    private const val COUNTER_SEED_BOUND = 0x800
    private const val RANDOM_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    private const val VARIANT_BITS = Long.MIN_VALUE

    private val random = SecureRandom()
    private var lastMillis = -1L
    private var counter = 0

    @Synchronized
    fun next(): UUID {
        val now = System.currentTimeMillis()
        if (now > lastMillis) {
            lastMillis = now
            counter = random.nextInt(COUNTER_SEED_BOUND)
        } else if (counter < COUNTER_MAX) {
            counter++
        } else {
            lastMillis++
            counter = 0
        }
        val mostSignificant = (lastMillis shl TIMESTAMP_SHIFT) or VERSION_BITS or counter.toLong()
        val leastSignificant = (random.nextLong() and RANDOM_MASK) or VARIANT_BITS
        return UUID(mostSignificant, leastSignificant)
    }

    /** The Unix epoch milliseconds a UUIDv7 was generated at. */
    fun timestampMillis(uuid: UUID): Long {
        require(uuid.version() == VERSION) { "Not a UUIDv7: $uuid" }
        return uuid.mostSignificantBits ushr TIMESTAMP_SHIFT
    }
}
