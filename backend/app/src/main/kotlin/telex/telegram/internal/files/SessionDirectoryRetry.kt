package telex.telegram.internal.files

import org.springframework.scheduling.annotation.Scheduled

/** Drains [SessionDirectories]' failed deletions once a minute. */
class SessionDirectoryRetry(
    private val directories: SessionDirectories,
) {
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    fun run() = directories.retryFailed()
}
