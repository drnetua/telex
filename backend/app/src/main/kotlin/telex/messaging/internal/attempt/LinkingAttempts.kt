package telex.messaging.internal.attempt

import org.springframework.stereotype.Component
import telex.identity.OwnerId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** The open linking attempts, at most one per Owner, with a per-Owner lock that serializes their steps. */
@Component
class LinkingAttempts {
    private val attempts = ConcurrentHashMap<OwnerId, LinkingAttempt>()
    private val locks = ConcurrentHashMap<OwnerId, ReentrantLock>()

    fun <T> locked(
        owner: OwnerId,
        block: () -> T,
    ): T {
        val lock = locks.computeIfAbsent(owner) { ReentrantLock() }
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    fun find(owner: OwnerId): LinkingAttempt? = attempts[owner]

    fun put(
        owner: OwnerId,
        attempt: LinkingAttempt,
    ) {
        attempts[owner] = attempt
    }

    fun remove(owner: OwnerId): LinkingAttempt? = attempts.remove(owner)

    fun owners(): List<OwnerId> = attempts.keys.toList()
}
