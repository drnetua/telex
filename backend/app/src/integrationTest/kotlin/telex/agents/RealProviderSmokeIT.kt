package telex.agents

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import telex.TestcontainersConfiguration
import telex.identity.OwnerId
import telex.llm.ChatMessage
import telex.llm.ImageInput
import telex.llm.ModelCatalog
import telex.llm.SlotAnswer
import telex.llm.SlotRequest
import telex.shared.Uuid7
import java.util.Base64

/**
 * Real-call smoke check (QG-5, NFR "Real call per slot"): text, vision and image each answered by the real provider
 * through the Balanced system profile. Skipped without TELEX_OPENROUTER_API_KEY. Prints only the answering model and
 * the fallback flag per slot, never the key or any content.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@EnabledIfEnvironmentVariable(named = "TELEX_OPENROUTER_API_KEY", matches = ".+")
class RealProviderSmokeIT {
    @Autowired lateinit var calls: ProfileCalls

    @Autowired lateinit var catalog: ModelCatalog

    @Autowired lateinit var jdbc: JdbcTemplate

    private val pixel =
        Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==",
        )

    private fun owner(): OwnerId {
        val id = Uuid7.next()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        return OwnerId(id)
    }

    private fun awaitCatalog() {
        val deadline = System.currentTimeMillis() + 60_000
        while (catalog.snapshot().models.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(500)
        assertThat(catalog.snapshot().models).`as`("catalog loaded at startup").isNotEmpty()
    }

    private fun answer(
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProfileCallResult.Answered {
        val result = calls.call(owner(), ProfileRef.System(SystemProfileKey.BALANCED), slot, request)
        val answered = result as? ProfileCallResult.Answered
        println(
            "smoke slot=${slot.wire} result=${result::class.simpleName} " +
                "model=${answered?.answeredBy?.value} fallback=${answered?.fallback}",
        )
        assertThat(result).isInstanceOf(ProfileCallResult.Answered::class.java)
        return answered!!
    }

    @Test
    fun `text vision and image slots are each answered by a real provider call (AC-225)`() {
        awaitCatalog()
        val text =
            answer(ModelSlotKind.TEXT, SlotRequest.Text(listOf(ChatMessage("user", "Reply with the word ok."))))
        assertThat((text.answer as SlotAnswer.Text).text).isNotBlank()

        val vision =
            answer(
                ModelSlotKind.VISION,
                SlotRequest.Vision(
                    listOf(ChatMessage("user", "Describe this image in one word.")),
                    listOf(ImageInput(pixel, "image/png")),
                ),
            )
        assertThat((vision.answer as SlotAnswer.Text).text).isNotBlank()

        val image = answer(ModelSlotKind.IMAGE, SlotRequest.Image("A plain red square."))
        assertThat((image.answer as SlotAnswer.Image).bytes).isNotEmpty()
    }
}
