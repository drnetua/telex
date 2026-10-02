# Button

Tabler `.btn`: 38px tall, 6px radius, weight 500. Intentional addition used by every component.

- One `primary` per card or dialog: the action the owner most likely wants.
- `secondary` (default) for other actions, `ghost` for low-emphasis ones, `danger` only for irreversible deletes; `danger-outline` is reserved for Stop all.
- Label = verb + object in sentence case. Add `kbd` to show a shortcut hint (Inbox).
- Consumer provides `children` (label), optional `icon`, `onClick`. Icon-only buttons need `ariaLabel`.
