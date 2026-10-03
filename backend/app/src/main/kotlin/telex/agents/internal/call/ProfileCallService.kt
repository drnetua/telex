package telex.agents.internal.call

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import telex.agents.ModelCallFinished
import telex.agents.ModelCallId
import telex.agents.ModelCallOutcome
import telex.agents.ModelSlotKind
import telex.agents.ProfileCallResult
import telex.agents.ProfileCalls
import telex.agents.ProfileRef
import telex.agents.SlotFailure
import telex.agents.internal.profile.ProfileRepository
import telex.agents.internal.profile.SystemProfiles
import telex.agents.internal.profile.toLlm
import telex.identity.OwnerId
import telex.llm.Attempt
import telex.llm.AttemptOutcome
import telex.llm.ModelCallFailure
import telex.llm.ModelCallResult
import telex.llm.ModelCalls
import telex.llm.ModelId
import telex.llm.SlotRequest
import telex.shared.Uuid7
import java.time.Clock
import java.time.Duration

internal fun AttemptOutcome.wire(): String = name.lowercase().replace('_', '-')

private val log = LoggerFactory.getLogger("telex.agents.call")

/** Resolves the profile's slot chain, calls `llm`, records the call without content and announces it. */
@Service
class ProfileCallService(
    private val systemProfiles: SystemProfiles,
    private val profiles: ProfileRepository,
    private val modelCalls: ModelCalls,
    private val records: CallRecordRepository,
    private val events: ApplicationEventPublisher,
    private val meters: MeterRegistry,
    private val clock: Clock,
) : ProfileCalls {
    override fun call(
        ownerId: OwnerId,
        profile: ProfileRef,
        slot: ModelSlotKind,
        request: SlotRequest,
    ): ProfileCallResult {
        val id = ModelCallId(Uuid7.next())
        val chains =
            when (profile) {
                is ProfileRef.System -> systemProfiles.profile(profile.key).slots
                is ProfileRef.Custom -> profiles.find(ownerId, profile.id)?.slots
            } ?: return ProfileCallResult.Failed(id, SlotFailure.PROFILE_NOT_FOUND, emptyList())
        val chain = chains[slot].orEmpty()
        val startedAt = clock.instant()
        val result =
            if (chain.isEmpty()) {
                ModelCallResult.Failed(ModelCallFailure.NO_MODEL_ANSWERED, emptyList())
            } else {
                modelCalls.call(chain, slot.toLlm(), request)
            }
        val finishedAt = clock.instant()
        val outcome = outcomeOf(result, chain)
        val answered = result as? ModelCallResult.Answered
        // A failed call counts as a fallback when a later model was tried or the missing main model was skipped.
        val fallback =
            answered?.fallback
                ?: (result.attempts.size > 1 || result.attempts.firstOrNull()?.outcome == AttemptOutcome.MISSING)
        record(slot, Duration.between(startedAt, finishedAt), outcome, fallback, result.attempts)
        runCatching {
            records.insert(
                CallRecord(
                    id,
                    ownerId,
                    profile,
                    slot,
                    outcome.wire(),
                    answered?.answeredBy,
                    fallback,
                    startedAt,
                    finishedAt,
                    result.attempts,
                ),
            ) {
                events.publishEvent(
                    ModelCallFinished(id, ownerId, profile, slot, outcome, answered?.answeredBy, fallback),
                )
            }
        }.onFailure { log.warn("Call record {} was not written: {}", id.value, it.javaClass.simpleName) }
        return when (result) {
            is ModelCallResult.Answered -> {
                ProfileCallResult.Answered(id, result.answer, result.answeredBy, result.fallback, result.attempts)
            }

            is ModelCallResult.Failed -> {
                ProfileCallResult.Failed(id, failureOf(outcome), result.attempts)
            }
        }
    }

    private fun outcomeOf(
        result: ModelCallResult,
        chain: List<ModelId>,
    ): ModelCallOutcome =
        when {
            result is ModelCallResult.Answered -> {
                ModelCallOutcome.ANSWERED
            }

            result is ModelCallResult.Failed && result.reason == ModelCallFailure.AI_NOT_CONFIGURED -> {
                ModelCallOutcome.AI_NOT_CONFIGURED
            }

            chain.isEmpty() || result.attempts.all { it.outcome == AttemptOutcome.MISSING } -> {
                ModelCallOutcome.NO_MODEL_AVAILABLE
            }

            else -> {
                ModelCallOutcome.NO_MODEL_ANSWERED
            }
        }

    private fun failureOf(outcome: ModelCallOutcome): SlotFailure =
        when (outcome) {
            ModelCallOutcome.AI_NOT_CONFIGURED -> SlotFailure.AI_NOT_CONFIGURED
            ModelCallOutcome.NO_MODEL_AVAILABLE -> SlotFailure.NO_MODEL_AVAILABLE
            else -> SlotFailure.NO_MODEL_ANSWERED
        }

    private fun ModelCallOutcome.wire(): String = name.lowercase().replace('_', '-')

    private fun record(
        slot: ModelSlotKind,
        elapsed: Duration,
        outcome: ModelCallOutcome,
        fallback: Boolean,
        attempts: List<Attempt>,
    ) {
        meters
            .counter(
                "telex.model.calls",
                "slot",
                slot.wire,
                "outcome",
                if (outcome == ModelCallOutcome.ANSWERED) "answered" else "failed",
                "fallback",
                fallback.toString(),
            ).increment()
        val share = if (attempts.isEmpty()) elapsed else elapsed.dividedBy(attempts.size.toLong())
        attempts.forEach {
            Timer
                .builder("telex.model.attempt")
                .tag("outcome", it.outcome.wire())
                .register(meters)
                .record(share)
        }
    }
}
