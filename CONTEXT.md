---
status: Living
updated_at: "2026-10-03"
---

# Domain Context — teleX

<!--
CONTEXT.md is the domain glossary — not a spec and not a scratch pad. NO implementation
detail here (no datastore/broker/framework names, no API contracts) — only domain words
and the boundaries between them. Implementation choices live in the SAD and ADRs; behaviour
lives in spec.md. The authored seed glossary is docs/docs/01-tech-spec.md §Глосарій; terms
move here as features first use them.
-->

## Glossary

- Fallback Chain — the ordered list of one to three models in a Model Slot; teleX uses the first model in it that is in the Model Catalog and answers, and moves to the next one when a model is gone or fails. NOT a cascade (trying a cheap model first and escalating to a pricier one when the answer is judged poor — teleX doesn't do that yet).
- Inbox — the one place in teleX where everything that waits for the Owner lands (drafts to approve, cases to check, blocked messages, tasks due today); the number of waiting items is the Inbox counter, the only counter in the navigation. NOT a Telegram chat list (Chats shows conversations; the Inbox shows what needs a decision).
- Linked Account — a Telegram account that an Owner has linked to teleX by signing in to it through teleX; it belongs to exactly one Owner, teleX holds its Telegram session encrypted, and it stays the same Linked Account when its session is lost and the Owner signs in to it again, until the Owner unlinks it, which ends the session and deletes everything teleX stored for it. NOT the Owner's teleX account (the Owner exists before and after any Linked Account), and NOT a Counterpart's account.
- Model Catalog — the list of AI models that this teleX installation can call, with what each model accepts and produces and what it costs, refreshed from the model provider automatically; a model that leaves the catalog can no longer be used. NOT a Model Profile (a named choice of models taken from the catalog).
- Model Profile — a named choice of models for an Owner's AI helpers, made of three Model Slots (text, vision, image); system profiles (Fast and cheap, Balanced, Careful) are set by the installation and shared by every Owner, custom profiles belong to exactly one Owner. NOT the Owner's profile page (email, passkeys and sessions on Profile and security).
- Model Slot — one job inside a Model Profile: text (read and write text), vision (understand images) or image (create images), filled with a Fallback Chain of models able to do that job; text is required, vision and image may stay empty. NOT a Tool (an action an Agent may take).
- Operator — the person who deploys and administers a teleX installation (quotas, model catalog, audit) and never sees the content of anyone's chats. NOT Owner (one person may hold both roles, but the rights of the two roles differ).
- Owner — a person with a teleX account who can link one or more Telegram accounts and owns everything created in them; someone becomes an Owner at sign-up, before any Telegram account is linked. NOT Counterpart (someone the Owner talks to in Telegram, who is not a teleX user).
- Passkey — a passwordless sign-in key that an Owner creates on their device or in a password manager and confirms with biometrics or a PIN; it belongs to exactly one Owner and has a name, a creation date and a last-used date. NOT a password (there is no shared secret that can be stolen from the server or phished).
- Sign-in Code — a 6-digit code sent in the same email as a Sign-in Link, typed into the browser that asked for the email; the code and the link are one single-use permission to sign in with the same 15-minute expiry, and 5 wrong codes void it. NOT the Sign-in Link (the link starts a session where it is opened; the code starts one where it is typed).
- Sign-in Link — a single-use link that teleX emails to an address; opening and confirming it starts a Sign-in Session in that browser (and creates the Owner for a new address); it works once, expires 15 minutes after it is sent (checked when it is confirmed), and using its Sign-in Code or requesting a newer sign-in email for the same address voids it. NOT the Owner Bot binding token (the one-time Start token in Telegram, E17).
- Sign-in Session — the signed-in state of one Owner in one browser or on one device; it starts after a Sign-in Link, a Sign-in Code or a Passkey and ends on sign-out, on revocation, after 30 days without activity (a page the Owner opens or an action they take; background refreshes don't count), or 90 days after it started, whichever comes first. NOT the Telegram session of a Linked Account (teleX's own sign-in to Telegram on the Owner's behalf; losing it does not sign the Owner out of teleX).
- Status Banner — a strip under the header on every signed-in screen that reports a condition affecting the Owner's whole teleX (offline, account disconnected, bot blocked, all assistants paused…) and offers one action; it stays until its cause is gone and cannot be dismissed, and when several apply the most important one shows with "N more". NOT a Toast (a short-lived message about one action that disappears by itself).
