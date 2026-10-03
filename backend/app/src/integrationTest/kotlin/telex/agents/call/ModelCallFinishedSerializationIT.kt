package telex.agents.call

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.events.core.EventSerializer
import telex.TestcontainersConfiguration
import telex.agents.ModelCallFinished
import telex.agents.ModelCallId
import telex.agents.ModelCallOutcome
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileRef
import telex.agents.SystemProfileKey
import telex.identity.OwnerId
import telex.llm.ModelId
import telex.shared.Uuid7

/** AC-229: the event survives the Modulith publication registry's serializer (events.md, ADR-0004). */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ModelCallFinishedSerializationIT {
    @Autowired lateinit var serializer: EventSerializer

    private fun event(profile: ProfileRef) =
        ModelCallFinished(
            ModelCallId(Uuid7.next()),
            OwnerId(Uuid7.next()),
            profile,
            ModelSlotKind.TEXT,
            ModelCallOutcome.ANSWERED,
            ModelId("it/a"),
            false,
        )

    @Test
    fun `system profile event round-trips through the registry serializer (AC-229)`() {
        val e = event(ProfileRef.System(SystemProfileKey.BALANCED))
        val back = serializer.deserialize(serializer.serialize(e), ModelCallFinished::class.java)
        assertThat(back).isEqualTo(e)
    }

    @Test
    fun `custom profile event round-trips through the registry serializer (AC-229)`() {
        val e = event(ProfileRef.Custom(ModelProfileId(Uuid7.next())))
        val back = serializer.deserialize(serializer.serialize(e), ModelCallFinished::class.java)
        assertThat(back).isEqualTo(e)
    }

    @Test
    fun `serialized profile has the documented tagged shape (AC-229)`() {
        val json = serializer.serialize(event(ProfileRef.System(SystemProfileKey.BALANCED))).toString()
        assertThat(json.replace(" ", "")).contains("\"profile\":{\"kind\":\"system\",\"key\":\"balanced\"}")
    }
}
