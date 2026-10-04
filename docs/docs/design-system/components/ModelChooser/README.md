# ModelChooser

Search plus the whole model catalog, shown under a slot of the profile editor (SCR-34). Intentional addition; row and disabled-row styling taken from `ChatPicker` (C-30).

- A region "Choose a model for <slot>" with a search box (filters by name or model id; focused on open), a "Hide list" button and one row per catalog model (name, provider).
- A row that can't be added stays in the list, marked `aria-disabled`, and says why with an icon and words: "Can't understand images" (vision), "Can't create images" (image), "Doesn't take and produce text" (text), "Already in this slot".
- Choosing an enabled row adds the model at the end of the chain and closes the list. Esc closes the list only, not the surrounding modal.
- No `ai` purple.
