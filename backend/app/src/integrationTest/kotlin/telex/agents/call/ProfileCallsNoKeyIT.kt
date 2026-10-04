package telex.agents.call

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import telex.TestcontainersConfiguration
import telex.agents.ModelCallFinished
import telex.agents.ModelCallOutcome
import telex.agents.ModelProfileId
import telex.agents.ModelSlotKind
import telex.agents.ProfileCallResult
import telex.agents.ProfileCalls
import telex.agents.ProfileRef
import telex.agents.SlotFailure
import telex.agents.SystemProfileKey
import telex.agents.internal.profile.ModelProfile
import telex.agents.internal.profile.ProfileRepository
import telex.identity.OwnerId
import telex.llm.ChatMessage
import telex.llm.ModelId
import telex.llm.SlotRequest
import telex.shared.Uuid7
import java.time.Instant
import java.util.UUID

/** AC-226 on the call path: no provider key fails plainly, with a record and zero attempts. */
@SpringBootTest(properties = ["telex.llm.openrouter.api-key="])
@RecordApplicationEvents
@Import(TestcontainersConfiguration::class)
class ProfileCallsNoKeyIT {
    @Autowired lateinit var calls: ProfileCalls

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

    @Autowired lateinit var profiles: ProfileRepository

    @Test
    fun `no provider key fails with ai-not-configured, zero attempts, and a record`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val r =
            calls.call(
                owner,
                ProfileRef.System(SystemProfileKey.BALANCED),
                ModelSlotKind.TEXT,
                SlotRequest.Text(listOf(ChatMessage("user", "hello"))),
            ) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.AI_NOT_CONFIGURED)
        assertThat(r.attempts).isEmpty()
        val row = jdbc.queryForList("SELECT * FROM model_call WHERE id = ?", r.callId.value).single()
        assertThat(row["outcome"]).isEqualTo("ai-not-configured")
        assertThat(row["owner_id"]).isEqualTo(id)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM model_call_attempt WHERE model_call_id = ?",
                Int::class.java,
                r.callId.value,
            ),
        ).isZero()
        val event =
            events
                .stream(ModelCallFinished::class.java)
                .filter { it.callId == r.callId }
                .toList()
                .single()
        assertThat(event.outcome).isEqualTo(ModelCallOutcome.AI_NOT_CONFIGURED)
    }

    @Test
    fun `no provider key wins over an empty slot - ai-not-configured, not no-model-available`() {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO owner VALUES (?, ?, ?, now())", id, "$id@mail.com", "$id@mail.com")
        val owner = OwnerId(id)
        val profile =
            ModelProfile(
                ModelProfileId(Uuid7.next()),
                "p-${UUID.randomUUID()}".take(20),
                mapOf(
                    ModelSlotKind.TEXT to listOf(ModelId("openai/gpt-x")),
                    ModelSlotKind.VISION to emptyList(),
                    ModelSlotKind.IMAGE to emptyList(),
                ),
            )
        profiles.insert(owner, profile, Instant.now())
        val r =
            calls.call(
                owner,
                ProfileRef.Custom(profile.id),
                ModelSlotKind.IMAGE,
                SlotRequest.Image("a cat"),
            ) as ProfileCallResult.Failed
        assertThat(r.reason).isEqualTo(SlotFailure.AI_NOT_CONFIGURED)
        assertThat(r.attempts).isEmpty()
        val row = jdbc.queryForList("SELECT * FROM model_call WHERE id = ?", r.callId.value).single()
        assertThat(row["outcome"]).isEqualTo("ai-not-configured")
    }
}
