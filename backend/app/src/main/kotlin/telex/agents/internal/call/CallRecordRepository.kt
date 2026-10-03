package telex.agents.internal.call

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import telex.agents.ModelCallId
import telex.agents.ModelSlotKind
import telex.agents.ProfileRef
import telex.identity.OwnerId
import telex.llm.Attempt
import telex.llm.ModelId
import java.sql.Timestamp
import java.time.Instant

/** What one finished call leaves behind. Holds ids, models and outcomes only, never request or answer text. */
data class CallRecord(
    val id: ModelCallId,
    val ownerId: OwnerId,
    val profile: ProfileRef,
    val slot: ModelSlotKind,
    val outcome: String,
    val answeredBy: ModelId?,
    val fallback: Boolean,
    val startedAt: Instant,
    val finishedAt: Instant,
    val attempts: List<Attempt>,
)

/** Insert-only store of call records (ADR-0004): the call row and its attempts go in one transaction. */
@Repository
class CallRecordRepository(
    private val jdbc: JdbcClient,
) {
    @Transactional
    fun insert(record: CallRecord) {
        val system = (record.profile as? ProfileRef.System)?.key?.wire
        val custom = (record.profile as? ProfileRef.Custom)?.id?.value
        jdbc
            .sql(
                "INSERT INTO model_call (id, owner_id, system_profile_key, custom_profile_id, slot, outcome, " +
                    "answered_by_model_id, fallback, started_at, finished_at) VALUES (:id, :owner, :system, " +
                    ":custom, :slot, :outcome, :answeredBy, :fallback, :startedAt, :finishedAt)",
            ).param("id", record.id.value)
            .param("owner", record.ownerId.value)
            .param("system", system)
            .param("custom", custom)
            .param("slot", record.slot.wire)
            .param("outcome", record.outcome)
            .param("answeredBy", record.answeredBy?.value)
            .param("fallback", record.fallback)
            .param("startedAt", Timestamp.from(record.startedAt))
            .param("finishedAt", Timestamp.from(record.finishedAt))
            .update()
        record.attempts.forEachIndexed { index, attempt ->
            jdbc
                .sql(
                    "INSERT INTO model_call_attempt (model_call_id, position, model_id, outcome) " +
                        "VALUES (:call, :position, :model, :outcome)",
                ).param("call", record.id.value)
                .param("position", index + 1)
                .param("model", attempt.modelId.value)
                .param("outcome", attempt.outcome.wire())
                .update()
        }
    }

    fun outcomeOf(id: ModelCallId): String? =
        jdbc
            .sql("SELECT outcome FROM model_call WHERE id = :id")
            .param("id", id.value)
            .query(String::class.java)
            .list()
            .singleOrNull()
}
