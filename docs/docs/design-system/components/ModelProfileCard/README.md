# ModelProfileCard

Tabler `card` for one model profile (SCR-66). Intentional addition, composes `Badge`, `Button` and the price line used by C-22.

- Heading is the profile name, always plain text. Badges: neutral "System" (icon `lock`) on system profiles, "Default" on the current default.
- Price per 100 runs, then one row per slot (text, vision, image): the chain with a neutral "Not in the catalog" badge (icon `circle-off`) for missing models, "Not used" when empty, "No model available" with a next step, and the fallback warning (icon `alert-triangle`) when the main model is unavailable.
- Next step on a no-model slot: "Pick another model" on a custom card, "Choose another profile" on a system card.
- Actions: system cards have Duplicate only; custom cards have Edit, Duplicate, Delete. With AI not set up, Edit and Duplicate are disabled and Delete stays.
- Status is always icon plus words; no `ai` purple (nothing here is AI output).
