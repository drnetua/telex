import type { ReactNode } from "react";
import type { IconName } from "../components/Icon/Icon";
import { messages } from "../messages";
import { useAccountDisconnected } from "./accountDisconnected";

const c = messages.shell.banner;

/** What a condition offers: retry the connection, go somewhere to fix the cause, or run its own action. */
export type ConditionAction =
  | { kind: "retry" }
  | { kind: "link"; label: string; to: string }
  | { kind: "button"; label: string; onClick: () => void; busy?: boolean };

/** What a condition resolves at render time from live data; whatever it leaves out falls back to the catalog. */
export interface ConditionLive {
  message?: string;
  action?: ConditionAction;
  /** Rendered beside the line, e.g. a Toast for a refused action. */
  notice?: ReactNode;
}

export interface Condition {
  code: string;
  icon: IconName;
  message: string;
  action: ConditionAction;
  /** A hook the banner calls inside the condition's own line, so it may read queries and own state. */
  useLive?: () => ConditionLive;
}

interface Entry extends Condition {
  /** Lower is more important (fixed order: user decision 2026-10-03). */
  importance: number;
}

const retry: ConditionAction = { kind: "retry" };

/** Catalog (ADR-0006, QG-3a): later epics add their code here and nowhere else in the shell. */
const catalog: readonly Entry[] = [
  { code: "offline", icon: "wifi-off", message: c.conditions.offline, action: retry },
  { code: "not-responding", icon: "cloud-off", message: c.conditions.notResponding, action: retry },
  {
    code: "account-disconnected",
    icon: "wifi-off",
    message: c.conditions.accountDisconnected,
    action: { kind: "link", label: messages.banner.openAccounts, to: "/accounts" },
    useLive: useAccountDisconnected,
  },
  {
    code: "bot-blocked",
    icon: "ban",
    message: c.conditions.botBlocked,
    action: { kind: "link", label: c.actions.unblock, to: "/settings" },
  },
  {
    code: "consent-needed",
    icon: "lock",
    message: c.conditions.consentNeeded,
    action: { kind: "link", label: c.actions.review, to: "/chats" },
  },
  {
    code: "budget-exhausted",
    icon: "alert-circle",
    message: c.conditions.budgetExhausted,
    action: { kind: "link", label: c.actions.budget, to: "/settings" },
  },
  {
    code: "all-assistants-paused",
    icon: "clock",
    message: c.conditions.allAssistantsPaused,
    action: { kind: "link", label: c.actions.resume, to: "/assistants" },
  },
  {
    code: "triage-deferred",
    icon: "info-circle",
    message: c.conditions.triageDeferred,
    action: { kind: "link", label: c.actions.details, to: "/inbox" },
  },
].map((entry, importance) => ({ ...entry, importance }) as Entry);

const byCode = new Map(catalog.map((e) => [e.code, e]));
const logged = new Set<string>();

/** Known conditions in the fixed importance order; an unknown code is logged once and dropped. */
export function orderConditions(codes: readonly string[]): Condition[] {
  const known: Entry[] = [];
  for (const code of new Set(codes)) {
    const entry = byCode.get(code);
    if (entry) known.push(entry);
    else if (!logged.has(code)) {
      logged.add(code);
      console.warn(`Unknown status condition ignored: ${code}`);
    }
  }
  return known.sort((a, b) => a.importance - b.importance);
}

/** Test helper: forget which unknown codes were already logged. */
export function resetUnknownConditions() {
  logged.clear();
}
