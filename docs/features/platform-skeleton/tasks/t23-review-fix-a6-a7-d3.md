---
id: T23
title: "Harden passkey registration: long emails, concurrent user-entity insert, honest 5xx, one transaction"
layer: "wiring"
deps: []
acs: ["AC-89", "AC-102"]
files_hint: ["backend/app/src/main/resources/db/migration/V202610021203__create_passkey_tables.sql", "docs/features/platform-skeleton/data-model.md", "backend/app/src/main/kotlin/telex/identity/Passkeys.kt", "backend/app/src/main/kotlin/telex/web/security/PasskeyCeremonies.kt", "backend/app/src/integrationTest/kotlin/org/springframework/security/web/webauthn/api/TestCredentialRecords.kt", "backend/app/src/integrationTest/kotlin/telex/web/PasskeyCeremoniesIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/PasskeysApiIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A6, A7, D3)"
status: "todo"
---

# T23 — Harden passkey registration: long emails, concurrent user-entity insert, honest 5xx, one transaction

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A6, A7, D3 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-89, AC-102 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Tests prove: an Owner with a 254-char email gets registration options (display_name fits — widen the unreleased migration column to 254 and update data-model.md, or truncate); two concurrent ensureUserEntity calls both succeed; a non-verification failure (e.g. DB down) surfaces as 5xx `unavailable`, only verification failures map to 400 passkey-registration-failed; a registered passkey is stored once with last_used NULL in one transaction. TestCredentialRecords moves out of org.springframework.* into a telex test package and stops using reflection.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T23` and one `SDD-AC:` trailer per AC.
