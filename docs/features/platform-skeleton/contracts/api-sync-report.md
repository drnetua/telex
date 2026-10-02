---
status: Draft
owner: "Anton Husiev (Backend Lead)"
updated_at: "2026-10-02"
feature_size: M
---

# API sync report — platform-skeleton

**Inputs read:** `data-model.md` ✓ (present — default path) · `sad.md` ✓ (`target_surfaces: [backend-service, web-frontend]` → OpenAPI + `events.md`; §6: 3 critical flows + 11 per-story flows) · `spec.md` ✓ (§4 US-41, US-01, US-45, US-46, US-47, US-49; §5 24 ACs) · ADR-0001…0006 · `architecture-map.md` §Conventions · `telex/shared/Problems.kt`, `telex/web/ProblemHandler.kt`.
**Outputs:** `contracts/openapi.yaml` (OpenAPI 3.1, 15 operations; `spectral:oas` lint 0 errors / 0 warnings), `contracts/events.md` (1 event).
**Size / route:** M / standard (from `.size` / `.route`).

## A. Field origins

| schema_path | origin | confidence |
|---|---|---|
| requestSignInEmail.email (req) | data-model.md → sign_in_grant.email VARCHAR(254); rule from spec AC-83 + sad §8 Email identity | high |
| requestSignInEmail.grantId | data-model.md → sign_in_grant.id (UUIDv7) | high |
| requestSignInEmail.email (resp) | data-model.md → sign_in_grant.email (echo, as typed) | high |
| previewSignInLink.linkToken | data-model.md → sign_in_grant.link_token_hash (plaintext only in the email; ADR-0003) | high |
| previewSignInLink.email | data-model.md → sign_in_grant.email | high |
| redeemSignInLink.linkToken | data-model.md → sign_in_grant.link_token_hash | high |
| redeemSignInCode.grantId | data-model.md → sign_in_grant.id | high |
| redeemSignInCode.code | data-model.md → sign_in_grant.code_hash (6 digits, ADR-0003) | high |
| *.createdAccount (SignInResult) | derived — redeem's created-account flag (data-model "Not stored"; SignInSessionStarted) | medium |
| X-Telex-Time-Zone header | data-model.md → sign_in_session.time_zone VARCHAR(64), `UTC` default | high |
| telex_session cookie | data-model.md → sign_in_session.key_hash (ADR-0001) | high |
| Problem.attemptsLeft | derived — 5 − sign_in_grant.wrong_attempts (CHECK 0–5) | high |
| Problem.email | data-model.md → sign_in_grant.email | high |
| getMe.ownerId | data-model.md → owner.id | high |
| getMe.email | data-model.md → owner.email (address as created) | high |
| getMe.linkedAccountCount | inferred from sad §6 Flow US-01 landing ("Linked Account count of zero"); no store before E02 | low |
| listMySessions.items[].id | data-model.md → sign_in_session.id | high |
| listMySessions.items[].userAgentLabel | data-model.md → sign_in_session.user_agent_label VARCHAR(100) | high |
| listMySessions.items[].deviceType | data-model.md → sign_in_session.device_type CHECK enum (4 values) | high |
| listMySessions.items[].startedAt | data-model.md → sign_in_session.started_at | high |
| listMySessions.items[].lastActivityAt | data-model.md → sign_in_session.last_activity_at | high |
| listMySessions.items[].current | derived — row's key_hash = this request's cookie hash | medium |
| endMySession.sessionId | data-model.md → sign_in_session.id | high |
| listMyPasskeys.items[].id | data-model.md → user_credentials.credential_id VARCHAR(1000) | high |
| listMyPasskeys.items[].label | data-model.md → user_credentials.label VARCHAR(1000) | high |
| listMyPasskeys.items[].createdAt | data-model.md → user_credentials.created (NULL) | high |
| listMyPasskeys.items[].lastUsedAt | data-model.md → user_credentials.last_used (NULL = "Never used") | high |
| removeMyPasskey.passkeyId | data-model.md → user_credentials.credential_id | high |
| passkeyRegistrationOptions.* | framework — Spring Security 7 `PublicKeyCredentialCreationOptions` (ADR-0002); `user.name` = user_entities.name (OwnerId), `displayName` = owner.email | high |
| registerPasskey.publicKey.* | framework — Spring `RelyingPartyPublicKey`; `label` overridden server-side → user_credentials.label | high |
| passkeyAuthenticationOptions.* | framework — Spring `PublicKeyCredentialRequestOptions` | high |
| signInWithPasskey.* (req) | framework — Spring assertion JSON; resolves user_credentials → user_entities.name → owner.id | high |
| event SignInSessionStarted.ownerId / sessionId / createdAccount | sad §5 + ADR-0004 → owner.id, sign_in_session.id, redeem flag | high |

No field without an origin. One `low` row (`linkedAccountCount`) is declared incompleteness: `--reconcile` tightens it when E02 adds the Linked Account store.

## B. Drift findings

1. **Endpoint ↔ data-model** *(core)* — ✓ Every `/api/v1` endpoint reads or writes ≥1 entity: grants → `sign_in_grant` (+ `owner`, `sign_in_session` on redeem); sign-out, sessions → `sign_in_session`; me → `owner`; passkeys and WebAuthn → `user_entities` / `user_credentials` (+ `sign_in_session` on `/login/webauthn`). No data-model table lacks an endpoint (`event_publication` is internal → `events.md`).
2. **Error code ↔ repo error definition** *(core)* — ✓ after resolution (user decision 2026-10-02): codes are **lowercase kebab-case DNS-1123 labels**. That conflicts with the repo today. `ProblemHandler.VALIDATION_FAILED = "validation_failed"`, `INTERNAL_ERROR = "internal_error"`, and framework codes are derived as `HttpStatus.name.lowercase()` (`not_found`, `method_not_allowed`), so they come out snake_case. **Required in `implement`:** rename both constants to `validation-failed` / `internal-error`, derive framework codes with `_` → `-`, map Bean Validation field codes (`NotBlank`, `Pattern`…) to kebab labels (`required`, `email-incomplete`, `code-format`), update `ProblemHandlerTest`, and add the rule to `architecture-map.md` §Conventions and `CLAUDE.md` §Errors. New domain codes this contract introduces: `unauthenticated`, `session-ended`, `sign-in-link-expired`, `sign-in-link-used`, `sign-in-grant-void`, `sign-in-code-wrong`, `not-found` (from sad §8) and `mail-unavailable`, `passkey-rejected`, `passkey-registration-failed`, `forbidden` (needed by §6 branches that sad §8 did not list). Security-filter errors (401, 403, WebAuthn failures) don't pass through `ProblemHandler`, so `web/security` needs an entry point, an access-denied handler and WebAuthn failure handlers that render the same problem.
3. **Validation ↔ constraint** *(core)* — ✓ `maxLength` 254 (email), 100 (userAgentLabel), 64 (time zone), 1000 (credential id, label), `enum` deviceType = data-model CHECK (`phone, tablet, computer, unknown`; sad §8 Device naming lists 3 + "neutral fallback" — data-model's explicit 4 wins), code `^[0-9]{6}$`, attemptsLeft 1–4 vs CHECK 0–5. `Passkey.createdAt` is nullable to match Spring's DDL (`created` NULL), and teleX always sets it.
4. **OpenAPI ↔ sequence** *(supporting)* — ✓ with one sequence gap, resolved (user decision 2026-10-02):
   - Every §6 `alt` branch has a response: invalid address → 400; mail unavailable → 503; expired/superseded → 410 `sign-in-link-expired`; used → 410 `sign-in-link-used`; void / 5th wrong → 410 `sign-in-grant-void`; wrong code → 422; no cookie → 401 `unauthenticated`; ended/idle/90 days → 401 `session-ended`; not this Owner's → 404 `not-found`; passkey verification fails → 400 `passkey-registration-failed`; unknown/removed credential → 401 `passkey-rejected`; sign-out failure and every "no answer in 10 s or server failure" → client timeout / 5xx (`internal-error`, `mail-unavailable`) → SCR-93.
   - **Sequence gap — unknown link token / grant id.** No §6 flow covers a token or grant id that never existed (garbled or truncated link). Resolved in the contract: answer 410 `sign-in-link-expired` **without** `email`, so the refusal page sends the person to SCR-01 to type the address. Saved as OQ below for `sequences`.
   - **Refusal needs the address.** "Send a new link" on SCR-08's refusal page resends "to the same address" (AC-35), but SCR-08 may open in a browser that never saw the address. So grant refusals carry the `email` extension, taken from `sign_in_grant.email`. §6 doesn't show this payload. It is consistent with the flows and no OQ is needed.
   - Orphan sequences: none. Flows US-41 (compose) and US-49 (client routing, SCR-91) have no endpoint by nature. US-49's server side is the shared 401 / 5xx responses.

### Resolutions (4-state)

| # | Finding | Action | Note |
|---|---|---|---|
| 1 | Error code spelling: sad §8 kebab vs repo snake_case, + 4 codes absent from §8 | Fix the contract (user: kebab-case DNS-1123 label) | Repo rename listed under B.2 is an `implement` task. sad §8's code list is superseded by `ErrorCode` here |
| 2 | Unknown link token / grant id has no §6 branch | Save as OQ (owner `sequences`) | Contract behaviour fixed: 410 `sign-in-link-expired`, no `email` |
| 3 | Cursor pagination on sessions / passkeys lists | Accept as is (user) | Whole lists `{items}`; per-Owner lists stay small, Spring's credential repository returns all |
| 4 | Idempotency-Key on retriable mutations (SCR-93 Retry) | Accept as is (user) | Request-email converges on "newest email wins". Known risk: a redeem whose response is lost, then retried, answers `sign-in-link-used`, and the person requests a new email. No schema change |

## C. Deviations from the sdd contract defaults

| Default | This contract | Authority |
|---|---|---|
| `BearerAuth` global | `SessionCookie` (apiKey in cookie `telex_session`) + `X-XSRF-TOKEN` on every POST/DELETE | ADR-0001, sad §8 CSRF |
| `{code, message, details?}` envelope | RFC 9457 `application/problem+json` + `code`, `errors[]`; per-code extensions `attemptsLeft`, `email` | architecture-map §Conventions, `telex.shared.problemDetail` |
| `code` = `module.error_name` | lowercase kebab-case DNS-1123 label, no module prefix | user decision 2026-10-02 |
| All paths under `/api/v1` | WebAuthn on `/webauthn/register/options`, `/webauthn/register`, `/webauthn/authenticate/options`, `/login/webauthn` | ADR-0002 |
| Cursor pagination on lists | whole `{items}` lists | user decision 2026-10-02 |
| Idempotency-Key on retriable mutations | none | user decision 2026-10-02 |

Other contract decisions (no default existed): JSON properties are camelCase (Jackson default, matches Spring's WebAuthn JSON). Secrets travel only in bodies, never in URLs: the Sign-in Link targets an SPA route with the token in the fragment, which is why "read link" is a `POST …/link/preview`. The browser time zone is the `X-Telex-Time-Zone` header, because Spring's `/login/webauthn` body can't carry it. Sign-out is public and idempotent. `DELETE /sessions/{id}` on the current session is allowed. ADR-0003's `/api/sign-in/...` path becomes `/api/v1/sign-in/...`.

## Open questions raised

- [ ] Draw the unknown-token branch (a link token or grant id that matches no grant → "link has expired", no address, back to SCR-01) in Critical flow 1 and Flow US-01 sign in by code. The contract already answers 410 `sign-in-link-expired` without `email`. — owner: `sequences` (Anton Husiev), due: before the contract is finalized (`/sdd:api platform-skeleton --reconcile`)

## Self-check

Structural self-check passed: 15/15 operations map to a §4 story. All 24 §5 ACs map to ≥1 response; AC-33 is covered by the whole surface and the compose stack, not by one operation. Every §6 `alt` branch has a response. Every operation has request (where it has a body), success and error examples with placeholder data only (`example.test`).

| AC | Operation / response |
|---|---|
| AC-33 | the whole surface reachable at `TELEX_PUBLIC_URL`; `requestSignInEmail` → local mailbox |
| AC-34 | `redeemSignInLink` 200 `createdAccount`; `getMe.email` |
| AC-82 | `redeemSignInCode` 200 |
| AC-35 | `previewSignInLink` / `redeemSignInLink` / `redeemSignInCode` 410 `sign-in-link-expired` + `email` |
| AC-83 | `requestSignInEmail` 400 `email-incomplete` |
| AC-84 | 410 `sign-in-link-used` |
| AC-85 | 410 `sign-in-grant-void`; 422 `attemptsLeft` |
| AC-86 | `previewSignInLink` (read-only) then `redeemSignInLink` |
| AC-103 | `requestSignInEmail` supersede; 410 `sign-in-link-expired`; code bound to `grantId` |
| AC-104 | `HeldSessionCookie` on the three redeem / sign-in operations |
| AC-89 | `registerPasskey`, `listMyPasskeys`, `signInWithPasskey` |
| AC-90 | client-only (capability check); email sign-in unchanged |
| AC-105 | `registerPasskey` 400 `passkey-registration-failed` |
| AC-91 | `createdAccount` (SCR-09 only then); `listMyPasskeys` empty |
| AC-92 | `removeMyPasskey` 204; `signInWithPasskey` 401 `passkey-rejected` |
| AC-93 | `listMySessions` (`current`), `endMySession`, 401 `session-ended` |
| AC-94 | `endMyOtherSessions` |
| AC-95 | `signOut` + `Cache-Control: no-store` |
| AC-96 | 401 `session-ended`; `X-Telex-Background` |
| AC-97 | 404 `not-found` on `endMySession` / `removeMyPasskey`; owner-scoped lists |
| AC-98 | `X-Telex-Time-Zone`; `events.md` |
| AC-100 | `getMe.linkedAccountCount` |
| AC-101 | 401 `unauthenticated` → SCR-01 with remembered page |
| AC-102 | 5xx (`internal-error`, `mail-unavailable`) + client 10 s timeout → SCR-93 |
