package telex.telegram

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import telex.TestcontainersConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * QG-1a: a real `tdlight` client on a temp directory, opened with a raw 32-byte key. The key appears nowhere teleX
 * persists or prints: not in any column of the key and account tables or the event registry, not in the captured
 * log, and not in any byte of the session directory.
 *
 * Needs the TDLib natives (linux x64/arm64 or macOS arm64); skipped elsewhere. Works offline: a client reaches the
 * phone step without a network.
 */
@SpringBootTest(
    properties = [
        "telex.telegram.adapter=tdlight",
        "telex.telegram.api-id=1",
        "telex.telegram.api-hash=0123456789abcdef0123456789abcdef",
        "telex.telegram.use-test-dc=true",
        "logging.level.telex=DEBUG",
    ],
)
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class TdlibKeyAtRestIT {
    @Autowired lateinit var sessions: TelegramSessions

    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `the raw TDLib key is not in the database, the log or any byte of the session directory`(
        output: CapturedOutput,
    ) {
        assumeTrue(nativesSupported(), "no TDLib natives for this platform")
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

        val id = sessions.open(key)
        sessions.close(id)
        try {
            verifyNothingLeaked(id, key, output)
        } finally {
            sessions.destroy(id)
        }
    }

    private fun verifyNothingLeaked(
        id: TelegramSessionId,
        key: ByteArray,
        output: CapturedOutput,
    ) {
        val encodings =
            listOf(
                key,
                HexFormat.of().formatHex(key).toByteArray(),
                HexFormat
                    .of()
                    .withUpperCase()
                    .formatHex(key)
                    .toByteArray(),
                Base64.getEncoder().encode(key),
                Base64.getUrlEncoder().withoutPadding().encode(key),
            )
        val directory = sessionsRoot.resolve(id.value.toString())
        val files = Files.walk(directory).use { paths -> paths.filter(Files::isRegularFile).toList() }
        assertThat(files).isNotEmpty()
        files.forEach { file ->
            val bytes = Files.readAllBytes(file)
            encodings.forEach { assertThat(indexOf(bytes, it)).describedAs("%s", file.fileName).isEqualTo(-1) }
        }

        val rows =
            listOf("linked_account", "owner_key", "event_publication").flatMap { table ->
                jdbc.queryForList("SELECT t::text AS row FROM $table t").map { it["row"].toString() }
            }
        val text = rows.joinToString("\n") + "\n" + output.all
        encodings.map { String(it, Charsets.ISO_8859_1) }.forEach { assertThat(text).doesNotContain(it) }
    }

    private fun nativesSupported(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val x64 = arch == "amd64" || arch == "x86_64"
        val arm = arch == "aarch64" || arch == "arm64"
        return (os.contains("linux") && (x64 || arm)) || (os.contains("mac") && arm)
    }

    private fun indexOf(
        haystack: ByteArray,
        needle: ByteArray,
    ): Int {
        for (start in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[start + it] == needle[it] }) return start
        }
        return -1
    }

    private companion object {
        const val KEY_BYTES = 32
        val sessionsRoot: Path = Files.createTempDirectory("telex-tdlib-it")

        @JvmStatic
        @DynamicPropertySource
        fun sessionsDir(registry: DynamicPropertyRegistry) {
            registry.add("telex.telegram.sessions-dir") { sessionsRoot.toString() }
        }
    }
}
