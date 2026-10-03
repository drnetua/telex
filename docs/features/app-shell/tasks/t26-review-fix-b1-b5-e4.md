---
id: T26
title: "e2e: tighten the shell and preference proofs: stable narrowed AC-102, fresh data after recovery, another browser, A/B in one browser, any-section counter, theme/zone reload checks, in-page load timing"
layer: "tests"
deps: ["T20", "T22", "T23", "T24", "T25"]
acs: ["AC-102", "AC-173", "AC-174", "AC-175", "AC-176", "AC-179", "AC-180", "AC-181", "AC-183"]
files_hint: ["e2e/tests/", "e2e/support/"]
owner: "Anton Husiev"
estimate: "M"
source: "review-2026-10-03 (B1, B2, B3, B4, B5, E4)"
status: "todo"
---

# T26 — e2e: tighten the shell and preference proofs: stable narrowed AC-102, fresh data after recovery, another browser, A/B in one browser, any-section counter, theme/zone reload checks, in-page load timing

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows B1, B2, B3, B4, B5, E4 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-102, AC-173, AC-174, AC-175, AC-176, AC-179, AC-180, AC-181, AC-183 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Playwright, both projects, green twice in a row: narrowed AC-102 hangs **/api/** (pulse included) and asserts the banner deterministically; recovery changes the fixture count while offline and asserts the new count within 5 s with no reload; expired session on /runs → SCR-92 → sign in → /runs; a link opened in a second context lands on /inbox; signing B out and A in within the same browser never shows B's count; AC-174 runs one step on a Coming soon section with a __reloadMarker; AC-179 reloads and opens dark; AC-180 asserts the switch stays on System; AC-181 first-device uses recordThemes from first parse; AC-183 covers the 'not on the list' fallback; the load NFR measures in-page from navigation start to the counter's first render with headroom.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm --filter @telex/e2e run check` and the Playwright run against `docker compose -f compose.yaml -f compose.e2e.yaml up -d --build --wait` (phone + desktop projects).
- Commit with `SDD-Task: T26` and one `SDD-AC:` trailer per AC.
