# AppShell

C-01 · The app frame for every screen after onboarding.

- Desktop: 240px side menu with the seven sections, Overview first; phone (`layout="phone"`): header + bottom nav with the first five (Tasks and Settings move to the header menu).
- Only Inbox shows a live counter. The active section uses `primary-subtle` + `primary`.
- The header always holds Stop all (C-03); the `banner` slot under it holds C-04.
- Consumer provides `active`, `inboxCount`, `accounts` (C-02), `banner`, `stopped` and the page as `children`.
