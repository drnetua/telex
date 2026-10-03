---
id: T31
title: "e2e: make the narrowed AC-102 test outlast the action's 10 s timeout"
layer: "tests"
deps: []
acs: ["AC-176", "AC-102"]
files_hint: ["e2e/tests/system-pages.spec.ts"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-2 (R2-3)"
status: "todo"
---

# T31 — e2e: make the narrowed AC-102 test outlast the action's 10 s timeout

Follow-up from the second independent review. The findings, with `file:line` citations and suggested fixes, are rows R2-3 in [`_review/review-2026-10-03-2.md`](../_review/review-2026-10-03-2.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-176, AC-102 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

system-pages.spec.ts waits for the client's own abort of /api/v1/passkeys (requestfailed, timeout 15 s), then asserts the banner is visible, no 'teleX is unavailable' heading, and the URL is still /profile.

- RED first (failing test quoted), then GREEN, then the per-task gate: the e2e spec against the docker stack.
- Commit with `SDD-Task: T31` and one `SDD-AC:` trailer per AC.
