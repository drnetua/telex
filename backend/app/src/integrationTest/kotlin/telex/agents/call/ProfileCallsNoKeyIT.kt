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
import telex.agents.ModelSlotKind
import telex.agents.ProfileCallResult
import telex.agents.ProfileCalls
import telex.agents.ProfileRef
import telex.agents.SlotFailure
import telex.agents.SystemProfileKey
import telex.identity.OwnerId
import telex.llm.ChatMessage
import telex.llm.SlotRequest
import java.util.UUID

/** AC-226 on the call path: no provider key fails plainly, with a record and zero attempts. */
@SpringBootTest(properties = ["telex.llm.openrouter.api-key="])
@RecordApplicationEvents
@Import(TestcontainersConfiguration::class)
class ProfileCallsNoKeyIT {
    @Autowired lateinit var calls: ProfileCalls

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: ApplicationEvents

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
}
