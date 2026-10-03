# ModelProfilePicker

C-22 · Choose a model profile: system or custom.

- Each option shows an estimated price per 100 runs; warns when the fallback model is in use.
- "Create your own" opens SCR-34.
- Props: `profiles`, `value`, `onChange`, `busy` (the option being saved shows a spinner and the other radios are read-only).
- Price states: estimate "≈ $0.30 per 100 runs", "< $0.01 per 100 runs", "Free", "Price unknown" (still choosable). A profile without a text model shows "No model available for text", no price, and a disabled radio.
- A fallback text slot shows "Main model unavailable. Using <model> for now."
