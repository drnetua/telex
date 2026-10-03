---
status: Draft
owner: "Anton Husiev (Backend Lead)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: S
---

# Public API — `ProfileCalls` (in-process port)

The `agents` module-root API that other parts of teleX (E14 runs, triage) use to get an answer from a
profile's slot (sad §3, §5, feature ADR-0002/0003). This is what AC-224, AC-228 and AC-229 are about. It is
an in-process Kotlin contract, not HTTP: in E10 its only callers are tests and the QG-5 smoke check.
Signatures are indicative. `tasks` and `implement` may adjust names, but not the shapes or the semantics.

```kotlin
package telex.agents

interface ProfileCalls {
    /** One attempt per chain model, falling back inside the call. Never throws for a model failure. */
    fun call(ownerId: OwnerId, profile: ProfileRef, slot: ModelSlotKind, request: SlotRequest): ProfileCallResult
}

sealed interface ProfileRef {                         // = openapi ProfileRef, = (system_profile_key | custom_profile_id)
    data class System(val key: SystemProfileKey) : ProfileRef   // FAST | BALANCED | CAREFUL ("fast", "balanced", "careful")
    data class Custom(val id: ModelProfileId) : ProfileRef
}

enum class ModelSlotKind { TEXT, VISION, IMAGE }      // "text" | "vision" | "image"

sealed interface SlotRequest {                        // content passes through to the provider, never stored or logged
    data class Text(val messages: List<ChatMessage>) : SlotRequest            // TEXT
    data class Vision(val messages: List<ChatMessage>, val images: List<ImageInput>) : SlotRequest  // VISION
    data class Image(val prompt: String) : SlotRequest                         // IMAGE
}

sealed interface ProfileCallResult {
    val callId: ModelCallId                            // model_call.id
    val attempts: List<Attempt>                        // model_call_attempt rows, in order

    data class Answered(
        override val callId: ModelCallId,
        val answer: SlotAnswer,                        // text, or image bytes + media type
        val answeredBy: ModelId,                       // model_call.answered_by_model_id (AC-224)
        val fallback: Boolean,                         // model_call.fallback (AC-224, AC-229)
        override val attempts: List<Attempt>,
    ) : ProfileCallResult

    data class Failed(
        override val callId: ModelCallId,
        val reason: SlotFailure,                       // model_call.outcome
        override val attempts: List<Attempt>,          // each model tried and its reason (AC-228)
    ) : ProfileCallResult
}

enum class SlotFailure { NO_MODEL_AVAILABLE, NO_MODEL_ANSWERED, AI_NOT_CONFIGURED, PROFILE_NOT_FOUND }

data class Attempt(val modelId: ModelId, val outcome: AttemptOutcome)

enum class AttemptOutcome(val movesOn: Boolean) {      // model_call_attempt.outcome
    ANSWERED(false),
    MISSING(true), UNAVAILABLE(true), PROVIDER_ERROR(true), RATE_LIMITED(true), TIMEOUT(true), TOO_LARGE(true),
    CONTENT_REFUSED(false), INVALID_REQUEST(false),
}
```

## Semantics

| Rule | Source |
|---|---|
| The chain is the slot's models in position order. Models not in the catalog, or (system overrides) not able to do the slot's job, are skipped as `MISSING` with no provider call. | AC-10, AC-227, sad §4 |
| Each remaining model gets one attempt within `telex.llm.attempt-timeout` (60 s). A move-on outcome tries the next model with the same request, and the caller never retries. | AC-224, ADR-0003 |
| `CONTENT_REFUSED` or `INVALID_REQUEST` stops at once: `Failed(NO_MODEL_ANSWERED)`, no further models. | AC-228 |
| All models fail → `Failed(NO_MODEL_ANSWERED)` with every attempt. | AC-228 |
| No model left, or an empty vision or image slot → `Failed(NO_MODEL_AVAILABLE)` with no provider call, and **never** the model of another slot. | AC-223 |
| No provider key → `Failed(AI_NOT_CONFIGURED)`, zero attempts. | AC-226 |
| `Custom` id not among `ownerId`'s profiles → `Failed(PROFILE_NOT_FOUND)`, same as a missing one. No call record, because there is no profile to record. | AC-222 |
| `fallback = true` when any model before `answeredBy` was skipped or failed, including a skipped missing main model. | AC-229 |
| Every call except `PROFILE_NOT_FOUND` writes one `model_call` row with its attempts and then publishes `ModelCallFinished` (events.md). A failed write is logged and doesn't change the result. | AC-229, ADR-0004 |
| The next call starts again from position 1. There is no memory of earlier failures. | AC-224 |
| Callers must already have passed consent and private-zone checks: this port doesn't see message context. | sad §11 |

`PROFILE_NOT_FOUND` and the row-skip for it are additions not shown in sad §6 flow 1 (see api-sync-report §B.4).
