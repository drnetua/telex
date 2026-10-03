package telex.inbox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InboxTest {
    private class FixedSource(
        private val count: Int,
    ) : InboxSource {
        val seen = mutableListOf<UUID>()

        override fun countWaiting(ownerId: UUID): Int {
            seen += ownerId
            return count
        }
    }

    @Test
    fun `no sources sums to zero`() {
        assertEquals(0, Inbox(emptyList()).countWaiting(UUID.randomUUID()))
    }

    @Test
    fun `one source returns its count`() {
        assertEquals(4, Inbox(listOf(FixedSource(4))).countWaiting(UUID.randomUUID()))
    }

    @Test
    fun `several sources are summed and each gets the same owner id`() {
        val a = FixedSource(2)
        val b = FixedSource(3)
        val owner = UUID.randomUUID()

        assertEquals(5, Inbox(listOf(a, b)).countWaiting(owner))
        assertEquals(listOf(owner), a.seen)
        assertEquals(listOf(owner), b.seen)
    }
}
