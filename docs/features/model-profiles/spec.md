---
status: Draft
owner: "Anton Husiev"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-03"
feature_size: "S"
---

# Spec — model-profiles

> **Glossary:** [CONTEXT](../../../CONTEXT.md) (repo root; no feature-level CONTEXT.md)
> **Reference module / docs / channels used:** `docs/docs/02-epics.md` §E10 (+ §E09, §E14, §E26, §E27 for boundaries) and the shared DoD · `docs/docs/03-product-spec.md` SCR-31, SCR-34, C-22, UC-12 · `docs/docs/01-tech-spec.md` §Глосарій (Model Profile, Budget), §Модель агента, §Вибір моделі · `docs/architecture-map.md` (`llm` module) · `docs/roadmap.md` step 10 and the execution waves · `docs/teleX-screens/Builder.html` and `Assistants.html` (ModelProfilePicker, assistant card states) · `backend/app/src/main/kotlin/telex/identity` (E01 reference module; `telex/llm` is still empty).

## 1. Context

Every AI helper in teleX needs a model, and the model market moves under it: the model provider teleX uses lists hundreds of models whose prices change and which come and go without notice. An Owner who sets up a helper has no way today to see which models exist, what they can do (read text, understand photos, draw images) or what they cost. And when a chosen model disappears, the helper would simply stop working. The Owner needs a short, understandable choice with a price attached, and the helper needs a backup plan that works without the Owner's help.

Why now: E10 sits in wave 3 of the roadmap, right after sign-in (E01), and it unlocks the operator console (E26) and the agent runtime (E14), which both need a model catalog and a way to resolve a profile into a working model. The agent builder (E09) and agent runs (E14) come later (waves 7 and 9), so E10 builds the model choice before anything uses it.

Committed approach: a Models page that shows the Model Catalog and the Owner's Model Profiles. Three system profiles (Fast and cheap, Balanced, Careful) come with default models that the Operator may override, and an Owner can duplicate one or build their own. A profile has three Model Slots (text, vision and image), each holding a Fallback Chain of up to three models able to do that slot's job. teleX always uses the first model in the chain that is in the catalog, and in the middle of a call it moves on to the next model when one fails. While a slot's main model is missing, the profile says so and names the model in use. The Owner picks a default profile in the same picker that E09 will later embed in the builder's "limits" block, and each option shows an estimated price per 100 runs. Success means that no AI work stops because one model vanished, and that the Owner can read the price of a choice before making it. (Ideation suite skipped at easy depth; the approach comes from the epic and the accepted assumption ledger.)

Traceability:
- AC-10 and AC-51 and US-13 keep their epic ids. New stories take the block US-80 onward and new criteria the block AC-210 onward, reserved for E10 so that specs written in parallel (telegram-link uses US-50…53 and AC-106…120, app-shell US-70…74 and AC-170…186) don't collide.
- Decision deviation: AC-51's picker lives on the Models page, not in the builder's "limits" block, because the builder is E09 (wave 7). The picker (C-22) is built here as a reusable component; the E09 card in `02-epics.md` now carries embedding it in the "limits" block (E09's spec writes that criterion, modelled on AC-51), and [ADR-0001](adr/0001-model-choice-ships-before-agents.md) records the move.
- Decision deviation: AC-10's warning appears on the Model Profile and its slot, not on the assistant card, because cards are E09 and runs are E14. Epic feature 4 (the warning on the assistant card) moves to the E09 card in `02-epics.md`, with a criterion modelled on AC-10, recorded in ADR-0001; E10 delivers the warning on the profile that the card will reuse.
- Decision deviation: choosing a profile in E10 sets the Owner's **default profile** (Balanced for a new Owner), which E09 will use for new agents. The epic doesn't name a default profile; it is what gives "choose a profile" a real effect before agents exist.
- Decision deviation: SCR-34 is a modal editor for a custom profile; E10 also adds a Models page (catalog + profiles + picker) to host it, which the screen inventory doesn't list. The roadmap already plans it (`pages/models`).
- Decision deviation: system profile names follow the mockup ("Fast and cheap", "Balanced", "Careful"), not the product spec's "швидкий, збалансований, якісний".
- Decision deviation: the tech spec's per-slot price ceiling (glossary Model Profile, "лімітом ціни") is dropped from E10, and no epic carries it; spending is capped by Budgets in E27 instead. Whether to bring it back is a §8 question.
- Decision deviation: the epic DoD's "each slot checked by a real model call" is a developer smoke check recorded in the pull request (§6), not a button in the UI.

## 2. Goals

- An Owner can see what each model can do and what it costs, and can pick a profile knowing roughly what 100 runs will cost.
- An Owner can shape their own profile with backups for every job, and can't save a profile that couldn't work.
- When a model disappears or fails, AI work continues on the next model in the chain instead of breaking; while a main model is missing the Owner is told, and every call is recorded without content (models tried, model that answered, fallback or not) for the run details and the fallback KPI.

## 3. Non-goals

- Choosing a profile per agent and showing fallback warnings on assistant cards. Agents don't exist until E09; E09 embeds the picker and the warning.
- Running agents, run history and the cost of a run. They are E14; E10 only provides the model choice that a Run will use.
- Budgets and paying with the Owner's own provider key (BYOK). They are E27; until then every call uses the installation's key.
- A price ceiling per slot. It is dropped, not deferred: no epic carries it, and budgets cap spending (§8).
- An Operator screen for curating the catalog or editing system profiles. That is E26; in E10 system profiles come with defaults and the Operator can override them in the installation settings.
- A "cascade" that tries a cheap model first and escalates when the answer is judged poor. It has no epic, and it changes cost behaviour in ways budgets must govern first.
- Filtering models by the provider's data-retention policy. It belongs with guardrails and privacy decisions (§8).
- Choosing the model used for semantic search. That model is installation-wide (E07), not part of an Owner's profile.

## 4. User stories

### US-13: Choose a model profile

**As an** Owner
**I want** to compare the system profiles and my own profiles by estimated price and pick one as my default
**So that** my AI helpers start on a model choice whose cost I understand

### US-80: Browse the model catalog

**As an** Owner
**I want** to see the models teleX can use, with what each one can do and what it costs
**So that** I can tell which models suit text, photos or images before I build a profile

### US-81: Build my own profile

**As an** Owner
**I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
**So that** my helpers use the models I trust, with backups I chose

### US-82: Keep working when a model disappears

**As an** Owner
**I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
**So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me

### US-83: Give Owners working defaults

**As an** Operator
**I want** to set the installation's provider key, and optionally override the default models behind the three system profiles, in the installation settings
**So that** every Owner gets working profiles without configuring anything

## 5. Acceptance criteria

### AC-51 (US-13) — happy

**Given** a signed-in Owner whose default profile is still Balanced (every new Owner starts with it) and who has one custom profile
**When** the Owner opens the profile picker on the Models page and switches the default to Careful
**Then** each option (Fast and cheap, Balanced, Careful and the custom profile) shows an estimated price per 100 runs, Careful becomes the default, and the Owner sees a confirmation naming it

### AC-210 (US-13) — error

**Given** the model currently used by a profile's text slot has no price in the Model Catalog
**When** the Owner looks at that profile in the picker
**Then** the profile shows "Price unknown" instead of an estimate, and the Owner can still choose it (a model priced at zero shows "Free", and an estimate below one cent shows "< $0.01 per 100 runs")

### AC-211 (US-80) — happy

**Given** a signed-in Owner and a loaded Model Catalog
**When** the Owner opens the catalog on the Models page, searches by name and filters by the vision slot
**Then** the Owner sees only models that can understand images and answer in text, each with its name, provider, what it accepts and produces, its price in US dollars (per million input and output tokens, or per image for models that create images) and how much text it can take at once, and sees when the catalog was last updated; the catalog lists only models that fit at least one slot (text: takes and produces text; vision: takes images and produces text; image: creates images)

### AC-212 (US-80) — error

**Given** the Model Catalog loaded earlier, and its latest automatic refresh has failed
**When** the Owner opens the catalog
**Then** the Owner sees the last known list with a note that it couldn't be updated and the time it is from, and profiles keep working with that list, also after teleX restarts; on a first start with no list ever loaded, the Owner sees "The model list isn't available yet", every slot shows no model available, and teleX retries every 5 minutes

### AC-213 (US-81) — happy

**Given** a signed-in Owner
**When** the Owner duplicates Balanced (the copy is first named "Balanced copy", then "Balanced copy 2" if that is taken), renames it "Cheap vision", puts two models in the text slot, moves the second one up so it becomes the main model, puts one model in the vision slot, leaves the image slot empty and saves
**Then** "Cheap vision" appears among the Owner's own profiles with its slots, chains in the chosen order, the image slot shown as "Not used" and the estimated price per 100 runs, and the Owner sees "Profile saved"; models of Balanced that were missing from the Model Catalog at the time are copied too, marked "Not in the catalog"

### AC-214 (US-81) — error

**Given** an Owner editing a custom profile
**When** the Owner saves it with a name that is empty or only spaces, longer than 40 characters after trimming, the same as another of their own profiles ignoring letter case, or the same as a system profile name
**Then** the profile isn't saved and the Owner sees which rule the name breaks ("Name the profile", "Use up to 40 characters", "You already have a profile called Cheap vision" or "Balanced is a system profile name")

### AC-215 (US-81) — domain invariant

**Given** an Owner editing a custom profile whose text slot has no models
**When** the Owner tries to save it
**Then** the profile isn't saved and the Owner is told that the text slot needs at least one model

### AC-216 (US-81) — domain invariant

**Given** an Owner filling the vision slot of a custom profile
**When** the Owner tries to add a model that can only read text
**Then** the model isn't added and the Owner is told that it can't understand images, so it can't go in the vision slot (the same rule holds for the image slot and models that can't create images, and for the text slot and models that don't both take and produce text)

### AC-217 (US-81) — domain invariant

**Given** an Owner editing a slot that already holds three models, or a slot that already contains a given model
**When** the Owner tries to add a fourth model, or the same model a second time
**Then** the model isn't added and the Owner is told that a slot holds at most three models, each only once

### AC-218 (US-81) — domain invariant

**Given** an Owner who already has 20 custom profiles
**When** the Owner tries to create or duplicate another one
**Then** nothing is created and the Owner is told that the limit is 20 custom profiles and that deleting one makes room

### AC-219 (US-81) — domain invariant

**Given** a signed-in Owner looking at the Balanced system profile
**When** the Owner tries to change or delete it
**Then** the change isn't possible, and the Owner is told that system profiles can't be changed and is offered to duplicate it instead

### AC-220 (US-81) — happy

**Given** an Owner whose default profile is their custom profile "Cheap vision"
**When** the Owner deletes "Cheap vision" and confirms
**Then** the profile disappears from their list, the default goes back to Balanced, and the Owner is told that Balanced is now the default

### AC-221 (US-81) — cross-context

**Given** an Owner who added a model to a custom profile in this edit, and that model has left the Model Catalog since the editor was opened; the profile also still holds an older model that left the catalog earlier
**When** the Owner saves the profile
**Then** the profile isn't saved and the Owner is told which newly added model is no longer available, so they can pick another; the older missing model is not a reason to refuse, and it stays in the chain marked "Not in the catalog" so that it is used again if it returns (AC-10)

### AC-222 (US-81) — authorization

**Given** two Owners, where the first has a custom profile "Night shift"
**When** the second Owner browses their profiles, opens a link to "Night shift" or tries to make it their default
**Then** the second Owner never sees "Night shift", and the link shows the same "not found" page as a profile that doesn't exist, so the profile's existence isn't revealed

### AC-10 (US-82) — error

**Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
**When** the Owner opens the Models page, and when teleX makes a text call with that profile
**Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again

### AC-223 (US-82) — error

**Given** a profile with a slot that has no model left in the Model Catalog, or an empty vision or image slot
**When** the Owner opens the Models page, and when teleX needs that slot for a call
**Then** the slot shows that no model is available for it, or "Not used" when it is empty (for a custom profile, with a prompt to pick another model; for a system profile, with a suggestion to choose another profile), and the call fails with that plain reason instead of being sent anywhere — never to the model of another slot; when it is the text slot, the picker shows the profile as "No model available for text" with no price, it can't be chosen as a new default, and Owners who already have it as their default keep it and see a warning suggesting another profile

### AC-224 (US-82) — cross-context

**Given** a profile whose text slot chain is model A then model B, both in the Model Catalog
**When** another part of teleX asks the profile's text slot for an answer, and model A is unavailable, returns a provider error, hits a rate limit, doesn't answer in time, or can't take a request that large
**Then** the same request is answered by model B without the caller retrying, and the result names model B as the model that answered and that a fallback happened; the next request starts again from model A

### AC-228 (US-82) — error

**Given** a profile whose text slot chain is model A then model B
**When** another part of teleX asks the slot for an answer and both models fail, or model A refuses the request because of its content, or the request itself is invalid
**Then** the request fails without trying further models after a content refusal or an invalid request, and the caller is told that no model in the text slot could answer, with each model tried and its reason

### AC-229 (US-82) — cross-context

**Given** an Owner whose AI work makes calls through a profile, some answered by the main model, some after a fallback and some failing
**When** the calls finish
**Then** each call is recorded without any message content: when it happened, the Owner, profile, slot, every model tried with its outcome, the model that answered (if any) and whether a fallback happened (skipping a main model that is missing from the catalog counts as one), so that run details and the fallback KPI read from one place

### AC-225 (US-83) — happy

**Given** an Operator who has set the installation's provider key as the README describes, and has either left the system profiles' models alone or overridden the models of Balanced in the installation settings
**When** the Operator starts teleX
**Then** the Model Catalog loads without further action, and every Owner sees the three system profiles: with the models that come with teleX where nothing was overridden, and with exactly the Operator's models for Balanced where it was

### AC-226 (US-83) — error

**Given** an installation started without a provider key
**When** an Owner opens the Models page
**Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log

### AC-227 (US-83) — error

**Given** an Operator who put a model that isn't in the Model Catalog, or one that can't do the slot's job, or more than three models, into a slot of a system profile (an override replaces that slot only; slots the Operator doesn't set keep the models that come with teleX)
**When** teleX starts or refreshes the catalog
**Then** teleX keeps running, the Operator finds a warning in the teleX log naming the profile, slot and model, that model is skipped as if it were missing (models beyond the third are ignored), and Owners see the fallback warning from AC-10 on that profile

## 6. Non-functional requirements

| Aspect | Target | Measurement |
|---|---|---|
| Models page load (catalog + profiles + picker) | p95 ≤ 1 s with a catalog of 500 models | Playwright timing on the CI build + server request-duration metric |
| Catalog freshness | refreshed at start and every 24 h, retried every 5 min after a failure; age ≤ 25 h while the provider is reachable; the last catalog survives a restart | integration test with a controlled clock and a restart |
| Provider outage | 0 failed Models page loads, profile saves and slot resolutions while the provider is down; the last known catalog stays in use | integration test with the provider fake down |
| In-call fallback | the next model is tried within the same request; a model that doesn't answer within the attempt timeout (default 60 s, §8) counts as failed | integration test with a provider fake that fails and stalls |
| Price estimate | per 100 runs of a typical run of 3,000 input + 500 output tokens on the text slot's current model, rounded to whole cents; "< $0.01" below one cent; "Free" at a price of zero; "Price unknown" when the catalog has no price | unit test over catalog prices |
| Real call per slot | text, vision and image each answered by a real provider call through a profile before the epic ships | smoke check with a real key, recorded in the E10 pull request (E10 DoD) |
| Responsive + accessible | the Models page and the profile editor work at 360 px and 1280 px and meet WCAG 2.2 AA | Playwright at both widths + automated accessibility scan with 0 violations |
| Availability | N/A — self-hosted single instance, no SLO in E10 | — |

## 6.1 Security / privacy

- **Data classification:** internal. Model names, prices and profile choices hold no chat content; only the installation's provider key is confidential.
- **Personal data touched:** the name an Owner gives a custom profile (free text, low sensitivity), each Owner's default profile choice, and a content-free record of each model call (time, Owner, profile, slot, models tried and outcomes; never message text, AC-229).
- **AuthZ/AuthN impact:** everything on the Models page needs a live Sign-in Session (E01). Custom profiles are only ever looked up among the caller's own (AC-222), system profiles are read-only for every Owner (AC-219), and only the Operator changes them, through the installation settings.
- **Secrets:** the provider key is set only in the installation settings and is never shown on a page, returned to the browser or written to logs.
- **Abuse cases:**
  - Cross-tenant access to another Owner's profile: hidden as if it doesn't exist (AC-222).
  - Script or markup in a profile name: always shown as plain text.
  - Spam-creating profiles: at most 20 custom profiles per Owner (AC-218).
  - Running up the installation's bill: nothing an Owner does in E10 calls a model; refreshing the catalog is free, and model calls start with E14 under budgets.
- **Security review:** N/A. There is no new personal data, ownership follows E01's rule (AC-97), and the key handling is covered by the regular `/sdd:review`.

## 7. Metrics / KPIs

- **Calls saved by fallback** (calls whose first model was missing or failed and that were answered by a later model ÷ all such calls; counted from content-free call records) — baseline: 0 (no calls exist); target: ≥ 99% in the first 30 days after E14 ships.
- **Catalog age** (hours since the last successful refresh, sampled hourly) — baseline: no catalog exists; target: ≤ 25 h in ≥ 99% of samples over the first 30 days of real use.
- **Estimate accuracy** (the text slot's estimated price per 100 runs vs the mean actual cost of 100 runs on that profile) — baseline: TBD until runs exist, measured from run costs once E14 ships; target: within ±30% for the three system profiles within 30 days of E14 shipping.

## 8. Open questions

- [ ] What happens to agents that use a custom profile when the Owner deletes it? Default now: deleting is allowed, and those agents move to the Owner's default profile with a warning on their card (E09 embeds the picker and the card warning per ADR-0001). — owner: Anton Husiev (PM), due: before `/sdd:specify agent-builder`
- [x] How long does one model attempt wait before it counts as failed and the next model is tried? Default now: 60 s. — owner: Anton Husiev (Tech Lead), due: before `/sdd:design model-profiles` — **resolved in design:** 60 s, configurable as `telex.llm.attempt-timeout` (sad.md §8, ADR-0003)
- [ ] Should Owners see, or filter by, whether a model's provider keeps the data sent to it (zero retention)? Default now: not shown. — owner: Anton Husiev (PM), due: before `/sdd:specify guardrails`
- [ ] Should a slot get a price ceiling (skip models above a set price), as the tech spec's Model Profile describes? Default now: dropped; Budgets in E27 cap spending. — owner: Anton Husiev (PM), due: before `/sdd:specify cost-control`
- [ ] Who pays for an Owner's model calls once BYOK exists — the installation key, the Owner's key, or the Owner's choice? Default now: the installation key. — owner: Anton Husiev (PM), due: before `/sdd:specify cost-control`
- [ ] Review 2026-10-03 B1 (AC-213, AC-227): a duplicated system profile whose Operator override names a model that is in the catalog but can't do the slot's job is refused with `model-not-capable` on save, and the editor doesn't mark that row "Not in the catalog" (`ProfileRules.kt:81-82`, `ProfileEditorModal.tsx:45`). Default now: the Owner removes or replaces that model before saving. — owner: Anton Husiev (Tech Lead), due: before `/sdd:specify agent-builder`
- [ ] Review 2026-10-03 B2 (AC-213): the suggested name for a duplicate ("… copy N") isn't trimmed, so duplicating a profile with a 36–40 character name gives a draft name over 40 characters, which the Owner must shorten (`ProfileRules.kt:92-101`). — owner: Anton Husiev (Tech Lead), due: before `/sdd:specify agent-builder`
- [ ] Review 2026-10-03 B3 (AC-216, AC-217): when the server refuses a model on save (`model-not-capable` / `model-duplicate`), the editor shows generic copy instead of the model chooser's slot-specific words (`ProfileEditorModal.tsx:390-395`, `messages.ts:224,226`). The add path in the chooser already uses the right words. — owner: Anton Husiev (Tech Lead), due: before `/sdd:specify agent-builder`
