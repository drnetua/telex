package telex.telegram.internal.files

import org.slf4j.LoggerFactory
import telex.telegram.TelegramSessionId
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * One directory per [TelegramSessionId] under [root]. A deletion that fails is queued and retried every minute by
 * [SessionDirectoryRetry]; whatever is still left after a restart is removed by [sweepOrphans] (ADR-0003).
 */
class SessionDirectories(
    private val root: Path,
    private val clock: Clock,
    private val deleter: (Path) -> Unit = ::deleteRecursively,
) {
    private val failed = ConcurrentHashMap<Path, Instant>()

    fun create(id: TelegramSessionId): Path = Files.createDirectories(dirOf(id))

    /** Removes the session's directory; a failure is logged and queued for [retryFailed]. */
    fun delete(id: TelegramSessionId) = deleteOrQueue(dirOf(id))

    /** Retries the queued deletions that have waited at least [RETRY_DELAY]. */
    fun retryFailed() {
        val now = clock.instant()
        failed.entries
            .filter { !now.isBefore(it.value.plus(RETRY_DELAY)) }
            .forEach { (path, _) ->
                failed.remove(path)
                deleteOrQueue(path)
            }
    }

    fun pendingRetries(): Int = failed.size

    /** Deletes every directory under the root whose name is not a referenced session id. */
    fun sweepOrphans(referenced: Set<TelegramSessionId>) {
        if (!root.isDirectory()) return
        val keep = referenced.mapTo(HashSet()) { it.value.toString() }
        Files
            .list(root)
            .use { children -> children.filter { it.isDirectory() && it.name !in keep }.toList() }
            .forEach(::deleteOrQueue)
    }

    private fun dirOf(id: TelegramSessionId): Path = root.resolve(id.value.toString())

    @Suppress("TooGenericExceptionCaught") // any failure to delete (locked file, permissions) is retried
    private fun deleteOrQueue(dir: Path) {
        try {
            if (dir.exists()) deleter(dir)
            failed.remove(dir)
        } catch (e: Exception) {
            log.warn("Could not delete session directory {}; will retry: {}", dir.name, e.message)
            failed[dir] = clock.instant()
        }
    }

    companion object {
        val RETRY_DELAY: Duration = Duration.ofMinutes(1)
        private val log = LoggerFactory.getLogger(SessionDirectories::class.java)

        fun deleteRecursively(dir: Path) {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
