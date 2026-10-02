---
id: T35
title: "Accept only a plain mailbox as an email address"
layer: "app"
deps: ["T34"]
acs: ["AC-83", "AC-34"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/internal/owner/EmailAddress.kt", "backend/app/src/test/kotlin/telex/identity/internal/owner/EmailAddressTest.kt", "docs/features/platform-skeleton/contracts/openapi.yaml"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R5)"
status: "todo"
---

# T35 — Accept only a plain mailbox as an email address

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R5 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-83, AC-34 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

EmailAddressTest: `Name<a@b.com>` and other display-name / group forms are rejected; the parsed InternetAddress must equal the input with no personal part; openapi.yaml EmailAddress description no longer claims the exact SPA rule (states plain RFC 5322 mailbox, SPA check is a subset hint).

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T35` and one `SDD-AC:` trailer per AC.
