# UndoBar

C-14 · The 10-second window after Send or an Act-level action.

- Countdown 10 → 0, then Sent; Undo returns the draft to the Inbox.
- Synced with the bot's undo message (TG-05). Several at once stack.
- Announced politely; never steals focus. Pass `live` to run the timer.
