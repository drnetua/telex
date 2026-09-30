package telex

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
    fun `writes module documentation`() {
        Documenter(modules).writeDocumentation()
    }
}
