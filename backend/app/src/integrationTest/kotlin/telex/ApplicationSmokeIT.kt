package telex

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ApplicationSmokeIT {
    @Test
    fun `application context boots`() {
        // Boots the whole app against a pgvector Postgres; a broken wiring fails here.
    }
}
