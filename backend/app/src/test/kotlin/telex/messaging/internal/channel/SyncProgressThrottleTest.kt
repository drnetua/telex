package telex.messaging.internal.channel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.identity.OwnerId
import telex.messaging.LinkedAccountId
import telex.messaging.LinkedAccountSyncProgressed
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** AC-116: progress events are at most one per second per account, and the last change is never lost. */
class SyncProgressThrottleTest {
    private class MutableClock(
        var now: Instant = Instant.parse("2026-10-03T10:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock()
    private val published = mutableListOf<LinkedAccountSyncProgressed>()
    private val scheduled = mutableListOf<Pair<Duration, Runnable>>()
    private val throttle =
        SyncProgressThrottle(clock, { published += it }) { delay, task -> scheduled += delay to task }
    private val owner = OwnerId(UUID.randomUUID())
    private val a = LinkedAccountId(UUID.randomUUID())
    private val b = LinkedAccountId(UUID.randomUUID())

    @Test
    fun `the first change publishes at once and later ones inside the second wait for one trailing publish`() {
        throttle.changed(owner, a)
        throttle.changed(owner, a)
        throttle.changed(owner, a)

        assertThat(published).hasSize(1)
        assertThat(scheduled).hasSize(1)
        assertThat(scheduled.single().first).isEqualTo(Duration.ofSeconds(1))

        clock.now = clock.now.plusSeconds(1)
        scheduled.single().second.run()

        assertThat(published).hasSize(2)
    }

    @Test
    fun `a change after a quiet second publishes at once`() {
        throttle.changed(owner, a)
        clock.now = clock.now.plusMillis(1_500)
        throttle.changed(owner, a)

        assertThat(published).hasSize(2)
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `accounts are throttled independently`() {
        throttle.changed(owner, a)
        throttle.changed(owner, b)

        assertThat(published.map { it.linkedAccountId }).containsExactly(a, b)
    }
}
