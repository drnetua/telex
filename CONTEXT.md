---
status: Living
updated_at: "2026-10-02"
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

- Operator — the person who deploys and administers a teleX installation (quotas, model catalog, audit) and never sees the content of anyone's chats. NOT Owner (one person may hold both roles, but the rights of the two roles differ).
- Owner — a person with a teleX account who can link one or more Telegram accounts and owns everything created in them; someone becomes an Owner at sign-up, before any Telegram account is linked. NOT Counterpart (someone the Owner talks to in Telegram, who is not a teleX user).
- Passkey — a passwordless sign-in key that an Owner creates on their device or in a password manager and confirms with biometrics or a PIN; it belongs to exactly one Owner and has a name, a creation date and a last-used date. NOT a password (there is no shared secret that can be stolen from the server or phished).
- Sign-in Code — a 6-digit code sent in the same email as a Sign-in Link, typed into the browser that asked for the email; the code and the link are one single-use permission to sign in with the same 15-minute expiry, and 5 wrong codes void it. NOT the Sign-in Link (the link starts a session where it is opened; the code starts one where it is typed).
- Sign-in Link — a single-use link that teleX emails to an address; opening and confirming it starts a Sign-in Session in that browser (and creates the Owner for a new address); it works once, expires 15 minutes after it is sent (checked when it is confirmed), and using its Sign-in Code or requesting a newer sign-in email for the same address voids it. NOT the Owner Bot binding token (the one-time Start token in Telegram, E17).
- Sign-in Session — the signed-in state of one Owner in one browser or on one device; it starts after a Sign-in Link, a Sign-in Code or a Passkey and ends on sign-out, on revocation, after 30 days without activity (a page the Owner opens or an action they take; background refreshes don't count), or 90 days after it started, whichever comes first. NOT the Telegram session of a Linked Account (teleX's own sign-in to Telegram on the Owner's behalf; losing it does not sign the Owner out of teleX).
