export type RefusalCode =
  | "telegram-account-owned-by-another-owner"
  | "telegram-account-already-linked"
  | "linked-account-limit-reached"
  | "telegram-account-mismatch";

export const REFUSAL_CODES: readonly string[] = [
  "telegram-account-owned-by-another-owner",
  "telegram-account-already-linked",
  "linked-account-limit-reached",
  "telegram-account-mismatch",
] satisfies RefusalCode[];

/** What replaces the wizard once the attempt has ended without a linked account. */
export type Outcome =
  | { kind: "wait"; retryAt: string }
  | { kind: "refused"; code: RefusalCode; limit?: number }
  | { kind: "ended"; reason?: string };
