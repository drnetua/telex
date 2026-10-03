package telex.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeZonesTest {
    @Test
    fun `known ids are accepted`() {
        listOf("Europe/Kyiv", "America/Argentina/Buenos_Aires", "UTC").forEach {
            assertTrue(TimeZones.isKnown(it), it)
        }
    }

    @Test
    fun `empty, legacy, etc and unknown ids are rejected`() {
        listOf("", " ", "EST", "GMT0", "Etc/GMT+3", "Europe/Atlantis", "SystemV/AST4", "a".repeat(65)).forEach {
            assertFalse(TimeZones.isKnown(it), it)
        }
    }

    @Test
    fun `list is sorted, unique, short and Area slash City or UTC`() {
        val ids = TimeZones.ids
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct(), ids)
        assertTrue("UTC" in ids)
        assertTrue(ids.all { it == "UTC" || it.contains('/') })
        assertTrue(ids.none { it.startsWith("Etc/") || it.startsWith("SystemV/") })
        assertTrue(ids.all { it.length <= 64 })
    }
}
