package telex.agents.internal.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import telex.agents.ModelSlotKind
import telex.agents.SystemProfileKey
import telex.llm.ModelId

class SystemProfilesTest {
    private val shipped =
        mapOf(
            "fast" to mapOf("text" to listOf("f/t"), "vision" to listOf("f/v"), "image" to listOf("f/i")),
            "balanced" to mapOf("text" to listOf("b/t"), "vision" to listOf("b/v"), "image" to listOf("b/i")),
            "careful" to mapOf("text" to listOf("c/t"), "vision" to listOf("c/v"), "image" to listOf("c/i")),
        )

    private fun ids(vararg s: String) = s.map { ModelId(it) }

    @Test
    fun `without overrides every profile keeps the shipped models and has its display name`() {
        val sp = SystemProfiles.merge(shipped, emptyMap())

        assertThat(sp.all().map { it.key }).containsExactly(
            SystemProfileKey.FAST,
            SystemProfileKey.BALANCED,
            SystemProfileKey.CAREFUL,
        )
        assertThat(sp.all().map { it.displayName }).containsExactly("Fast and cheap", "Balanced", "Careful")
        assertThat(sp.profile(SystemProfileKey.CAREFUL).slots[ModelSlotKind.IMAGE]).isEqualTo(ids("c/i"))
        assertThat(sp.unknownEntries).isEmpty()
    }

    @Test
    fun `overriding balanced text replaces that slot only`() {
        val sp = SystemProfiles.merge(shipped, mapOf("balanced" to mapOf("text" to listOf("x/1", "x/2"))))

        val balanced = sp.profile(SystemProfileKey.BALANCED).slots
        assertThat(balanced[ModelSlotKind.TEXT]).isEqualTo(ids("x/1", "x/2"))
        assertThat(balanced[ModelSlotKind.VISION]).isEqualTo(ids("b/v"))
        assertThat(balanced[ModelSlotKind.IMAGE]).isEqualTo(ids("b/i"))
        assertThat(sp.profile(SystemProfileKey.FAST).slots[ModelSlotKind.TEXT]).isEqualTo(ids("f/t"))
        assertThat(sp.profile(SystemProfileKey.CAREFUL).slots[ModelSlotKind.TEXT]).isEqualTo(ids("c/t"))
    }

    @Test
    fun `four models keep the first three and the fourth is ignored`() {
        val sp = SystemProfiles.merge(shipped, mapOf("balanced" to mapOf("text" to listOf("a", "b", "c", "d", "e"))))

        val balanced = sp.profile(SystemProfileKey.BALANCED)
        assertThat(balanced.slots[ModelSlotKind.TEXT]).isEqualTo(ids("a", "b", "c"))
        assertThat(balanced.ignored[ModelSlotKind.TEXT]).isEqualTo(ids("d", "e"))
    }

    @Test
    fun `unknown profile key or slot is reported and ignored`() {
        val sp =
            SystemProfiles.merge(
                shipped,
                mapOf(
                    "turbo" to mapOf("text" to listOf("x")),
                    "fast" to mapOf("audio" to listOf("y")),
                ),
            )

        assertThat(sp.unknownEntries).hasSize(2)
        assertThat(sp.unknownEntries.joinToString()).contains("turbo").contains("audio")
        assertThat(sp.all()).hasSize(3)
        assertThat(sp.profile(SystemProfileKey.FAST).slots[ModelSlotKind.TEXT]).isEqualTo(ids("f/t"))
    }
}
