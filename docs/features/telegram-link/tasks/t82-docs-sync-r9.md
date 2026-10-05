---
id: T82
title: "Sync the README, contract, screens, test plan and task files with the ninth-review fixes"
layer: "docs"
deps: ["T77", "T78", "T79", "T80", "T81"]
blocks: []
acs: []
files_hint: ["README.md", "docs/features/telegram-link/contracts/openapi.yaml", "docs/features/telegram-link/screens.md", "docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — D19, D20, D21, D22 (docs part)"
status: "done"
---

# T82 — Sync the README, contract, screens, test plan and task files with the ninth-review fixes

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), D19, D20, D21, D22 (docs part) (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** —
- **Blocked by:** T77, T78, T79, T80, T81 · **Blocks:** —

## What to change

- D19: `README.md:42` generates `TELEX_MASTER_KEY` inline before `docker compose up`, so a new shell gets a new key and startup refuses it. Generate it once into a file (`openssl rand -base64 32 > telex-master.key`, keep it outside the repo) and export it with `$(cat telex-master.key)`.
- D20: the `LinkingAttemptNotFound` example in `contracts/openapi.yaml:630` says "There is no open linking attempt."; the server sends "This linking attempt ended." (`LinkingProblems.kt:50`).
- D21: add the SCR ids and states to `t37-live-client-reconnect.md` (SCR-10/SCR-60 `live`) and `t49-banner-state-and-toast-announce.md` (SCR-02/10/60 banner, Toast).
- D22: record in t74 that the pending interrupt is taken at the end of `signOut` (plus T81's check before the delete), and in t75 that the test fakes `setInterval`/`clearInterval`/`Date` and advances by hand instead of `shouldAdvanceTime`.
- `screens.md`: the W17 unlink copy (`:245`) and the W15/W16 wait wording (Owner's zone, Retry-After). `test-plan.md`: the AC-111 e2e row, the busy-state component tests, the wait-card cases, the T81 edge case. Mark T77–T82 done in the tracker and task files.

## RED first

Docs only: no test. Check by `git grep` that the old example text and the inline `openssl rand` export are gone.

## Definition of Done

The README generates the master key once; the openapi example matches the server; T37 and T49 cite their SCR states; t74/t75 record how they were done; screens.md and test-plan.md describe T77–T81; tasks.json matches every task file. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
