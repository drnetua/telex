---
status: Accepted
owner: "Anton Husiev (PM)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "S"
ticket: "E10 model-profiles"
---

# 0001 — Model choice ships before agents; E09 embeds the picker and the card warning

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (specify interview + critic)

## Context

Epic E10 has two criteria that rely on screens owned by other epics. AC-51 puts the profile picker in the "limits" block of the agent builder. Feature 4 and AC-10 put the fallback warning on the assistant card. The builder and the cards are E09 (wave 7), and runs are E14 (wave 9). E10 itself sits in wave 3, and E26 and E14 depend on it. E09's card lists "вибір моделей (E10)" as out of scope. The shared Definition of Done in `docs/docs/02-epics.md` forbids stubs, and it requires a spec change, an ADR and an epic change before a feature leaves an epic.

## Decision drivers

- Shared DoD: no stubs, and every AC is covered by a green automated test.
- Roadmap order: E10 in wave 3, ahead of E09 (wave 7) and E14 (wave 9).
- E10's value ("if a model disappears, the helper doesn't break") must be observable when E10 ships.

## Considered options

1. **Ship the model choice on its own page, and let E09 embed it.** E10 builds the Models page: the catalog, the profiles, the reusable picker (C-22) with the price per 100 runs, the Owner's default profile, and the fallback warning on the profile. E09 embeds the picker in the "limits" block and shows the warning on assistant cards.
2. **Move E10 after E09 in the roadmap.** AC-51 and AC-10 hold word for word, but E26 and E14 slip, and the catalog would arrive late for everything else.
3. **Keep AC-51 and AC-10 in E10 as written.** E10 then can't close until E09 ships, or it needs a stand-in builder, which breaks the DoD.

## Decision outcome

**Chosen:** option 1. AC-10 and AC-51 keep their ids in E10 and are met on the Models page and the profile (spec `docs/features/model-profiles/spec.md`). E09 gains feature 8, "model profile picker in the "limits" block and fallback warning on the assistant card", with its own criteria modelled on AC-51 and AC-10. E09's out-of-scope line now names only the catalog and the profile editor. `02-epics.md` is patched in the same commit as the model-profiles spec.

## Consequences

- Good: E10 closes on its own value, with every AC testable now. E14 and E26 get the catalog and the profile resolution on schedule.
- Good: choosing a profile has a real effect before agents exist, because it sets the Owner's default profile, which E09 uses for new agents.
- Bad: E09's scope grows by one feature line and two criteria.
- Watch: what happens to agents that use a deleted custom profile is still open (spec §8), and must be settled before `/sdd:specify agent-builder`.
