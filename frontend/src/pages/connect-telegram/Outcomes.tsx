import { useEffect, useState } from "react";
import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import type { IconName } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import type { RefusalCode } from "./outcome";

const pad = (n: number) => String(n).padStart(2, "0");
const minutesSeconds = (total: number) => `${Math.floor(total / 60)}:${pad(total % 60)}`;

function secondsUntil(retryAt: string): number {
  return Math.max(0, Math.ceil((new Date(retryAt).getTime() - Date.now()) / 1000));
}

function clock(retryAt: string): string {
  const at = new Date(retryAt);
  return `${pad(at.getHours())}:${pad(at.getMinutes())}`;
}

interface WaitStateProps {
  retryAt: string;
  starting: boolean;
  onBack: () => void;
  onStartAgain: () => void;
}

/** Countdown to Telegram's retry time; the live region speaks at the start and at the end only. */
export function WaitState({ retryAt, starting, onBack, onStartAgain }: WaitStateProps) {
  const t = messages.linking;
  const [remaining, setRemaining] = useState(() => secondsUntil(retryAt));
  const [text] = useState(() => t.waitBody(clock(retryAt), minutesSeconds(remaining)));
  const over = remaining === 0;

  useEffect(() => {
    if (over) return;
    const timer = setInterval(() => setRemaining(secondsUntil(retryAt)), 1000);
    return () => clearInterval(timer);
  }, [retryAt, over]);

  const visible = over ? t.waitOver : t.waitBody(clock(retryAt), minutesSeconds(remaining));
  return (
    <EmptyState
      kind="blocked"
      focusTitle
      icon="clock"
      title={t.waitTitle}
      action={
        over ? (
          <Button busy={starting} onClick={onStartAgain}>
            {starting ? t.starting : t.startAgain}
          </Button>
        ) : (
          <Button className="btn-outline-secondary" onClick={onBack}>
            {t.back}
          </Button>
        )
      }
    >
      <span aria-hidden={!over}>{visible}</span>
      <span role="status" className="visually-hidden">
        {over ? t.waitOver : text}
      </span>
    </EmptyState>
  );
}

interface RefusedStateProps {
  code: RefusalCode;
  limit?: number;
  displayName?: string;
  onBack: () => void;
  onOpenAccounts: () => void;
}

export function RefusedState({
  code,
  limit,
  displayName,
  onBack,
  onOpenAccounts,
}: RefusedStateProps) {
  const t = messages.linking;
  const back = <Button onClick={onBack}>{t.back}</Button>;
  const accounts = (label: string) => <Button onClick={onOpenAccounts}>{label}</Button>;
  const view: Record<
    RefusalCode,
    { icon: IconName; title: string; body: string; action: React.ReactNode }
  > = {
    "telegram-account-owned-by-another-owner": {
      icon: "ban",
      title: t.otherOwnerTitle,
      body: t.otherOwnerBody,
      action: back,
    },
    "telegram-account-already-linked": {
      icon: "check",
      title: t.alreadyLinkedTitle,
      body: t.alreadyLinkedBody,
      action: back,
    },
    "linked-account-limit-reached": {
      icon: "ban",
      title: t.limitTitle,
      body: t.limitBody(limit ?? 0),
      action: accounts(t.openAccounts),
    },
    "telegram-account-mismatch": {
      icon: "ban",
      title: t.mismatchTitle,
      body: t.mismatchBody(displayName ?? ""),
      action: accounts(t.backToAccounts),
    },
  };
  const shown = view[code];
  return (
    <EmptyState
      kind="blocked"
      focusTitle
      icon={shown.icon}
      title={shown.title}
      action={shown.action}
    >
      {shown.body}
    </EmptyState>
  );
}

interface AttemptEndedStateProps {
  /** The problem code of the phone refusal that ended the attempt, when there was one. */
  reason?: string;
  starting: boolean;
  onBack: () => void;
  onStartAgain: () => void;
}

function refusalBody(reason: string | undefined): string | undefined {
  const known: Record<string, unknown> = messages.linking.problems;
  const text = reason ? known[reason] : undefined;
  return typeof text === "string" ? text : undefined;
}

export function AttemptEndedState({
  reason,
  starting,
  onBack,
  onStartAgain,
}: AttemptEndedStateProps) {
  const t = messages.linking;
  return (
    <EmptyState
      kind="blocked"
      focusTitle
      icon="clock"
      title={t.endedTitle}
      action={
        <div className="d-grid gap-2">
          <Button busy={starting} onClick={onStartAgain}>
            {starting ? t.starting : t.startAgain}
          </Button>
          <Button className="btn-ghost-secondary" disabled={starting} onClick={onBack}>
            {t.back}
          </Button>
        </div>
      }
    >
      {refusalBody(reason) ?? t.endedBody}
    </EmptyState>
  );
}

interface LoadFailedStateProps {
  onRetry: () => void;
  onBack: () => void;
}

/** The attempt could not be loaded: an inline state, so the card is never a dead end (AC-109). */
export function LoadFailedState({ onRetry, onBack }: LoadFailedStateProps) {
  const t = messages.linking;
  return (
    <EmptyState
      kind="blocked"
      focusTitle
      icon="cloud-off"
      title={t.loadFailed}
      action={
        <div className="d-grid gap-2">
          <Button onClick={onRetry}>{t.tryAgain}</Button>
          <Button className="btn-ghost-secondary" onClick={onBack}>
            {t.back}
          </Button>
        </div>
      }
    />
  );
}
