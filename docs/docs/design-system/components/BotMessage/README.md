# BotMessage

C-37 · The template for every teleX bot message (TG-01…TG-14), in Telegram's own look.

- Structure: assistant · account → body → Open in teleX → Why? → buttons.
- Up to 3 buttons per row, about 20 characters each. Telegram formatting only: bold, italic, quote, link. Symbols ✓ ✎ ✗ stand in for icons because inline buttons can't hold images.
- States: with buttons, without, after the action (summary replaces buttons), pinned counter.
- Colors here imitate Telegram's default theme and are not tokens.
