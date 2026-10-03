package telex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    private val modules = ApplicationModules.of(TelexApplication::class.java)

    @Test
    fun `module boundaries hold`() {
        modules.verify()
    }

    @Test
    fun `inbox is a module depending on shared only and shared stays bean-free`() {
        assertEquals(15, modules.count { it.identifier.toString() != "shared" })
        val inbox = modules.getModuleByName("inbox").orElseThrow()
        val dependencies =
            inbox
                .getDirectDependencies(modules)
                .uniqueModules()
                .map { it.identifier.toString() }
                .toList()
        assertTrue(dependencies.all { it == "shared" }, "inbox may depend on shared only: $dependencies")
        val shared = modules.getModuleByName("shared").orElseThrow()
        assertTrue(shared.springBeans.isEmpty())
    }

    @Test
    fun `writes module documentation`() {
        Documenter(modules).writeDocumentation()
    }
}
