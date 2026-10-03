package telex.telegram.internal.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import telex.shared.Uuid7
import telex.telegram.TelegramSessionId
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SessionDirectoriesTest {
    @TempDir
    lateinit var root: Path

    private var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = now
        }

    private fun newId() = TelegramSessionId(Uuid7.next())

    private fun directories(deleter: (Path) -> Unit = SessionDirectories::deleteRecursively) =
        SessionDirectories(root, clock, deleter)

    @Test
    fun `create makes a directory per session under the root`() {
        val id = newId()
        val dir = directories().create(id)

        assertThat(dir).isEqualTo(root.resolve(id.value.toString())).isDirectory()
    }

    @Test
    fun `delete removes the directory with its files`() {
        val dirs = directories()
        val id = newId()
        Files.writeString(dirs.create(id).resolve("td.binlog"), "x")

        dirs.delete(id)

        assertThat(root.resolve(id.value.toString())).doesNotExist()
        assertThat(dirs.pendingRetries()).isZero()
    }

    @Test
    fun `delete of a missing directory is a no-op`() {
        val dirs = directories()
        dirs.delete(newId())
        assertThat(dirs.pendingRetries()).isZero()
    }

    @Test
    fun `a failed delete is retried after one minute`() {
        var failing = true
        val dirs =
            directories { path ->
                check(!failing) { "locked" }
                SessionDirectories.deleteRecursively(path)
            }
        val id = newId()
        dirs.create(id)

        dirs.delete(id)
        assertThat(dirs.pendingRetries()).isEqualTo(1)
        assertThat(root.resolve(id.value.toString())).exists()

        now = now.plus(Duration.ofSeconds(59))
        dirs.retryFailed()
        assertThat(dirs.pendingRetries()).isEqualTo(1)

        failing = false
        now = now.plusSeconds(2)
        dirs.retryFailed()
        assertThat(dirs.pendingRetries()).isZero()
        assertThat(root.resolve(id.value.toString())).doesNotExist()
    }

    @Test
    fun `a retry that fails again stays queued`() {
        val dirs = directories { error("locked") }
        val id = newId()
        dirs.create(id)
        dirs.delete(id)

        now = now.plus(Duration.ofMinutes(1))
        dirs.retryFailed()

        assertThat(dirs.pendingRetries()).isEqualTo(1)
    }

    @Test
    fun `sweep keeps referenced directories and deletes the rest`() {
        val dirs = directories()
        val kept = newId()
        val orphan = newId()
        dirs.create(kept)
        Files.writeString(dirs.create(orphan).resolve("td.binlog"), "x")
        Files.createDirectory(root.resolve("not-a-session"))
        Files.writeString(root.resolve("stray-file"), "x")

        dirs.sweepOrphans(setOf(kept))

        assertThat(root.resolve(kept.value.toString())).exists()
        assertThat(root.resolve(orphan.value.toString())).doesNotExist()
        assertThat(root.resolve("not-a-session")).doesNotExist()
        assertThat(root.resolve("stray-file")).exists()
    }

    @Test
    fun `sweep on a missing root does nothing`() {
        SessionDirectories(root.resolve("absent"), clock).sweepOrphans(emptySet())
    }
}
