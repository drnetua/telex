package telex.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThemeTest {
    @Test
    fun `wire values are lowercase`() {
        assertEquals(listOf("light", "dark", "system"), Theme.entries.map { it.wire })
    }

    @Test
    fun `fromWire maps known values and rejects the rest`() {
        assertEquals(Theme.DARK, Theme.fromWire("dark"))
        assertEquals(Theme.SYSTEM, Theme.fromWire("system"))
        assertEquals(Theme.LIGHT, Theme.fromWire("light"))
        assertNull(Theme.fromWire("DARK"))
        assertNull(Theme.fromWire(""))
        assertNull(Theme.fromWire("auto"))
    }
}
