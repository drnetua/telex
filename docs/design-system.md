---
status: Living
tool: code
figma_file: ""
pen_file: ""
updated_at: "2026-10-03"
---

# Design system — teleX

> The project's **design canon**, produced once per repo by `design-system` and read by
> `ux-flows` / `screens` / `implement` / `review`. Committed: the tool choice and the inventory
> are team-wide, not per-developer. `architecture-map.md` §Frontend / UI foundation stays the
> inventory of the **code**. This file is the **design-side** canon (tool, posture, tokens,
> component inventory, cross-screen conventions). The authored brand, color, content and accessibility rules live in
> [`docs/docs/design-system/README.md`](docs/design-system/README.md); this canon cites them and doesn't repeat them.
> Refresh via `/sdd:design-system` when the foundation changes.

## Platform posture

- **Posture:** responsive-both. Every screen is designed and checked at phone and desktop widths together (D-04: all 38 screens fully responsive), and e2e runs both profiles (`e2e/playwright.config.ts`).
- **Breakpoints / device classes:** `bp-phone` 360 px (smallest supported width; phone e2e profile), `bp-tablet` 768 px (below it, the bottom navigation replaces the side menu), `bp-desktop` 1280 px (side panels open beside the content; desktop e2e profile) — `docs/docs/design-system/tokens.json` `breakpoint`.

## Design tool

- **Tool:** code. `screens.md` carries inline markdown wireframes that name components from the inventory below, as in `docs/features/platform-skeleton/screens.md`. No Figma or Pencil connection is used. Visual targets come from the Claude Design mockups (D-16), which are references to compare against, not the canon.
- **Library location:** the in-repo components in `frontend/src/components/` are the library. The reference implementation of every teleX component (C-01…C-41, a README and a preview each) is `docs/docs/design-system/components/<Name>/`, ported into `frontend/src/components/` on first use. Screen mockups are `docs/teleX-screens/*.html` and `docs/designs/scr-80-overview/` (render only in Claude Design).

## Token source

The source of truth is `docs/docs/design-system/tokens.json` (light and dark themes). In code the tokens are Tabler 1.6 CSS variables. Where Tabler's default differs from `tokens.json`, `frontend/src/styles.css` overrides it per theme (`:root` and `[data-bs-theme="dark"]`, e.g. `--telex-danger-text`, `frontend/src/styles.css:2`). The two are kept in sync by hand. A screen never declares a raw hex value or a one-off size.

- **Colors:** `tokens.json` `color` (`themes` + `tokens`: surfaces, text, `primary`, `ai`, status `-text`/`-subtle`, `focus-ring`, chart series) → Tabler variables + `frontend/src/styles.css` overrides. Usage rules: `docs/docs/design-system/README.md` §Color.
- **Spacing / sizing:** `tokens.json` `spacing` (`space-1` 4 px … Tabler spacers), `radius`, `shadow`, `size` (incl. `target-min` 24 px; 44 px on the phone bottom nav).
- **Typography:** `tokens.json` `type` (system `sans` stack, `mono`; heading/body/small/overline groups), with no web fonts. Usage rules: `docs/docs/design-system/README.md` §Type.

## Component inventory

Built components (`frontend/src/components/`) plus the reference components the app shell (E06) needs next. Other reference components (C-xx) join this table when a feature ports them, which `implement` registers.

| Component | Source (`file:line` / node / URL) | States it supports | Notes |
|---|---|---|---|
| Button | `frontend/src/components/Button/Button.tsx:10` | default / hover / focus / disabled / busy (spinner, label kept) | Variant via Tabler class (`btn-primary` default, `btn-ghost-dark`, `btn-danger`); optional icon |
| Badge | `frontend/src/components/Badge/Badge.tsx:11` | neutral / success / danger | Always icon plus words, never color alone |
| Icon | `frontend/src/components/Icon/Icon.tsx:38` | sizes (24 default) | Tabler Icons subset by name |
| CodeInput | `frontend/src/components/CodeInput/CodeInput.tsx:16` | empty / typing / invalid / disabled | 6-digit Sign-in Code entry |
| EmptyState | `frontend/src/components/EmptyState/EmptyState.tsx:14` | kind: none / blocked / first | One sentence and one action |
| LoadState | `frontend/src/components/LoadState/LoadState.tsx:7` | loading (skeleton rows) | The one loading pattern |
| Toast | `frontend/src/components/Toast/Toast.tsx:14` | info (polite, self-dismiss) / error (assertive, stays until dismissed) | Feedback on a single action; not for conditions affecting all of teleX |
| ThemeSwitch | `frontend/src/components/ThemeSwitch/ThemeSwitch.tsx:23` | variant `segmented` (radio group, icon + word) / `menu` (ghost button + `menuitemradio` dropdown, opens upward); save failed (error Toast with Try again) | The one theme control: apply at once, remember on this device, save, revert on failure |
| Toast `action` prop | `frontend/src/components/Toast/Toast.tsx:11` | optional one action (for example "Try again"); the toast stays until dismissed or acted on | Used by ThemeSwitch |
| ConfirmDialog | `frontend/src/components/ConfirmDialog/ConfirmDialog.tsx:27` | open / busy / confirm variant `danger` | Names the consequences; destructive confirm in `danger` |
| FailureBoundary | `frontend/src/app/FailureBoundary.tsx:13` | error | Routes render failures to the system pages (SCR-90) |
| AppShell (C-01) | `frontend/src/shell/AppShell/AppShell.tsx` | desktop side menu / phone bottom nav + header + More sheet; active section; Inbox counter; banner slot | Section list comes from `frontend/src/shell/sections.ts` |
| StatusBanner (C-04) | `docs/docs/design-system/components/StatusBanner/README.md` | one condition / most important + "N more"; not dismissable while its cause holds | Not yet ported; arrives with E06 `app-shell` (first condition: offline) |
| SidePanel (C-05) | `docs/docs/design-system/components/SidePanel/README.md` | desktop side panel / phone full-screen sheet | Not yet ported; moved to E14 (SCR-41) per `docs/features/app-shell/adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md` |
| StopAllButton (C-03) | `docs/docs/design-system/components/StopAllButton/README.md` | active / "Resume" | Not yet ported; arrives with E23 |
| AccountSwitcher (C-02) | `docs/docs/design-system/components/AccountSwitcher/README.md` | hidden (one account) / several / "All accounts" | Not yet ported; arrives with E02/E04 |

`screens.md` may use only these names (and any reference component it ports, cited by its README), or declare `NEW: <name>` with a reason no existing component fits. The new component is then registered back here.

## Interaction & writing conventions

- **Errors:** a problem with one field shows inline under that field. A failed action shows an error Toast that stays until dismissed and says what happened and what to do. A condition affecting all of teleX (offline, account disconnected…) shows a StatusBanner. Dead ends go to a system page (SCR-90). No apologies, no jokes, no exclamation marks (`docs/docs/design-system/README.md` §Content).
- **Empty states:** EmptyState with one sentence and one action, e.g. "Nothing needs you right now." + "Open chats".
- **Loading:** LoadState skeleton rows in place of the content. Buttons that start an action switch to `busy`. No full-page spinners.
- **Validation:** checked on submit, with errors attached under the field and the first invalid field focused. Server field errors map to the same fields.
- **Microcopy tone:** English, friendly and restrained (D-14, D-17), and sentence case. Buttons name the action and its object. No emoji. Every string lives in the one message catalog `frontend/src/messages.ts`.
- **Status and color:** status is never shown by color alone (icon plus words). `ai` purple is reserved for content an assistant produced. Tokens only, both themes, WCAG 2.2 AA (D-18).
