teleX is a web Telegram client where AI assistants read chats, draft replies and do small jobs, always under the owner's control. The interface is a calm work tool built on **Tabler** (`@tabler/core` 1.6.1): Tabler's default palette, type, spacing, radii and shadows, Tabler Icons, and a small set of teleX components for what Tabler lacks (chat, approvals, timers, undo, the AI trace).

## Principles

- **The owner decides.** Every AI action is visible, explained ("Why?") and reversible where possible. Never hide what an assistant did.
- **Overview to start, Inbox to act.** The start screen is the Overview dashboard (activity, spend, reliability and speed of the assistants) with a "Needs you" block; the daily loop lives in the Inbox. Other sections are for setup and investigation. Only the Inbox shows a counter in the nav.
- **Calm by default.** Neutral surfaces, one primary action per card, color only where it carries meaning.
- **Private stays private.** Chats in the private zone show a lock and never an AI trace.

## Content

The UI language is **English**. Tone is **friendly and restrained**: short, plain, helpful, never cute.

- Address the owner as "you"; assistants speak in the third person ("Reply bot drafted this").
- Sentence case for everything: buttons, titles, menu items. No Title Case, no ALL CAPS except the `overline` style.
- Buttons name the action and its object: "Approve and send", "Pause assistant", "Resume all". Never "OK" or "Submit".
- Errors say what happened and what to do: "Couldn't send. Telegram is rate-limiting this account. We'll retry in 30 s." No apologies, no jokes, no exclamation marks.
- Empty states are one sentence and one action: "Nothing needs you right now." + "Open chats".
- Numbers: money in dollars to the cent (`$0.03`, `< $0.01`, `≈ $1.20`); relative time under a day ("12 min ago"), dates after.
- No emoji in UI copy. Icons carry the visual cues.

## Color

Tabler's default palette, in light and dark themes. Always use tokens, never raw hex.

- Page: `bg-body`. Cards, panels, sidebar, header, inputs: `bg-surface`. Recessed areas inside a card (quoted original, table header): `bg-surface-secondary`. Row hover: `bg-hover`.
- Text: `text-heading` for titles and names, `text-body` for copy, `text-secondary` for meta. Nothing lighter than `text-secondary` for readable text.
- **Primary** (`primary`, Tabler blue) marks the single main action, links, the active nav item and selection. Text on it: `on-primary`. Selected rows: `primary-subtle`.
- **AI** (`ai`, Tabler purple) marks anything an assistant produced or touched: the AI trace ◉N, "Written by assistant", the sparkle icon, assistant badges. `ai-subtle` behind a drafted proposal. Never use `ai` for buttons or generic emphasis.
- **Status** uses Tabler's green, yellow, red and azure. The solid tokens (`success`, `warning`, `danger`, `info`) are for dots, fills and icons at 24px+. Words and small icons use the `-text` variants, which meet 4.5:1. Banners use the `-subtle` backgrounds with `-text` words.
- Status is never color alone: always an icon and a word too (C-24 Run status, C-04 Banner).
- Borders: `border` for decorative hairlines; `border-control` (3:1) for inputs, checkboxes and selects.

## Accessibility

Target: **WCAG 2.2 AA** in both themes.

- Text 4.5:1 on its ground (3:1 at 24px+ or bold 19px+); controls, focus rings and meaningful icons 3:1. Every text token's usage note lists its ratios.
- Focus: 2px solid `focus-ring` outline, 2px offset, on every interactive element. Never remove it.
- Targets at least `target-min` (24px); 44px for phone bottom nav.
- Timers (C-13 TTL, C-14 undo) are announced politely, never steal focus, and the owner can always act before they expire.
- Keyboard: every action reachable by keyboard; Inbox shortcuts J/K/Enter/E/X are shown in tooltips.
- Respect `prefers-reduced-motion`: card exit and toast slide become fades.

## Type

Tabler's system font stack (`sans`), no web fonts: fast and native on every OS. `mono` for YAML, model ids and token counts.

- Page title `h1`; section `h2`; card and panel titles `h3`; names in rows `h4`.
- Copy `body`; buttons, tabs and nav `body-medium`; meta `small`; table headers and eyebrows `overline`.

## Charts

The Overview (SCR-80) and Spend (SCR-62) use C-38 to C-41.

- Series colours come from Tabler, in this fixed order: `chart-1` blue, `chart-2` teal, `chart-3` indigo, `chart-4` pink, `chart-5` cyan, then `chart-other` gray. The order passes colour-blind separation checks on both themes; don't reorder or add hues. A sixth assistant folds into "Other".
- Colour follows the assistant, never its rank: an assistant keeps its slot on every chart and after any filter.
- Purple (`ai`) and the status colours never mark a series.
- One y-axis per chart. Columns at most 24px wide with a 4px rounded top, 2px gaps between stacked segments; lines 2px; gridlines 1px `border`.
- Text in charts uses text tokens, never the series colour. Always a legend for two or more series, and a table view behind every chart.
- One period picker (C-41) above everything it scopes.

## Space, radius, elevation

- Spacing follows Tabler's spacers: `space-1` 4px to `space-6` 40px. Card padding `space-4` on desktop, `space-3` on phone.
- Radii: buttons and inputs `radius-md` (6px), cards and panels `radius-lg`, message bubbles and phone sheets `radius-xl`, chips `radius-sm`, avatars and counters `radius-pill`.
- Elevation: cards `shadow-sm`, menus `shadow-md`, panels, toasts and dialogs `shadow-lg`. Borders do most of the work; shadows stay faint.

## Layout

- Breakpoints: `bp-phone` 360, `bp-tablet` 768, `bp-desktop` 1280. Every screen is fully responsive.
- Desktop: 240px side menu with seven sections (Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings) (`sidebar-width`), 56px header, content, and a 420px side panel (C-05) for "Why?", draft editing, tests and run details.
- Phone: header + bottom nav; the panel becomes a full-screen sheet.
- The "Stop all" button (C-03) is always visible in the header.

## Iconography

**Tabler Icons** (outline, 24px grid, 2px stroke, round caps), from `@tabler/icons-react` in code. The Icons asset group holds the set teleX uses; the SVGs there are drawn in `gray-700` for preview only, and in code icons take `currentColor`. Default size 20px in buttons and nav, 16px inline with `small` text. Do not mix in other icon sets or emoji.

Key mappings: Overview `layout-dashboard`, Inbox `inbox`, Chats `messages`, Assistants `sparkles`, Runs `activity`, Tasks `checklist`, Settings `settings`, Admin `shield-lock`, Stop all `hand-stop`, private chat `lock`, AI trace `sparkles`, Why? `help-circle`, undo `arrow-back-up`, blocked `ban`.

## Logo

The teleX logo (chat character with an orbit and a spark) lives in the Logos group. Use it on sign-in, onboarding and the collapsed sidebar only. Keep clear space of at least `space-4` around it; do not recolor it.
