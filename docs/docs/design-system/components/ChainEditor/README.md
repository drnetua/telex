# ChainEditor

One slot's Fallback Chain inside the profile editor (SCR-34). Intentional addition, composes `Badge` and `Button`; row styling follows `ChatPicker` (C-30).

- A `fieldset` named for the slot (Text / Vision / Image). Rows in order: position 1 is a neutral "Main" label, then "Backup 1", "Backup 2".
- Each row: the model name (the model id when it isn't in the catalog), a neutral "Not in the catalog" `Badge` (icon `circle-off`) when so, and icon buttons "Move up", "Move down", "Remove <model>". The first row can't move up, the last can't move down.
- Focus stays on the moved row's button after a move (the opposite button if the same one became disabled); after a removal it goes to the neighbouring row, or to "Add model" when the chain is empty. Every move is announced in a polite live region ("<model> is now Main").
- "Add model" opens a `ModelChooser`. A slot holds at most three models: at three, the button is disabled and "A slot holds at most three models." sits beside it.
- Empty chain shows a secondary-text hint ("Add at least one model." for text, "Not used" for vision and image).
- Errors: one for the whole slot and one per row, each `invalid-feedback` with an icon and words.
- No `ai` purple; status never by color alone.
