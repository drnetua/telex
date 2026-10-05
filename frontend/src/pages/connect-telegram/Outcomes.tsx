import { useEffect, useRef, useState } from "react";
import { useMe } from "../../api/account";
import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import type { IconName } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { formatInstant } from "../../shell/time";
import type { RefusalCode } from "./outcome";

const pad = (n: number) => String(n).padStart(2, "0");
const minutesSeconds = (total: number) => `${Math.floor(total / 60)}:${pad(total % 60)}`;

function secondsUntil(until: number): number {
  return Math.max(0, Math.ceil((until - Date.now()) / 1000));
}

/** HH:mm in the Owner's saved zone, like every other time teleX shows. */
function clock(retryAt: string, timeZone: string | null): string {
  return formatInstant(retryAt, timeZone, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
}

interface WaitStateProps {
  retryAt: string;
  until: number;
  starting: boolean;
  onBack: () => void;
  onStartAgain: () => void;
  /** Writes the wizard's polite live region, which is in the page before this card and outlives it. */
  announce: (text: string) => void;
}

/** Countdown to Telegram's retry time; the live region speaks at the start and at the end only. */
export function WaitState({
  retryAt,
  until,
  starting,
  onBack,
  onStartAgain,
  announce,
}: WaitStateProps) {
  const t = messages.linking;
  const me = useMe();
  const timeZone = me.data?.timeZone ?? null;
  const [remaining, setRemaining] = useState(() => secondsUntil(until));
  const time = clock(retryAt, timeZone);
  const settled = !me.isPending;
  const over = remaining === 0;
  const card = useRef<HTMLDivElement>(null);
  const backHadFocus = useRef(false);
  const announced = useRef(false);

  // The wizard's region was in the page, empty, before this card: a region that arrives with its text is not reliably
  // spoken. It speaks once at the start, when the Owner's zone is known (or failed to load), and once at 0:00.
  useEffect(() => {
    if (over) {
      announce(t.waitOver);
    } else if (settled && !announced.current) {
      announced.current = true;
      announce(t.waitBody(time, minutesSeconds(remaining)));
    }
  }, [announce, over, settled, t, time, remaining]);

  // Leaving the card silences the region; a remount (StrictMode) speaks the start again.
  useEffect(
    () => () => {
      announced.current = false;
      announce("");
    },
    [announce],
  );

  // Back is replaced by Start again at 0:00; when that drops the focused button, focus returns to the heading.
  useEffect(() => {
    if (over && backHadFocus.current) {
      card.current?.querySelector<HTMLElement>("[tabindex='-1']")?.focus();
    }
  }, [over]);

  useEffect(() => {
    if (over) return;
    const timer = setInterval(() => {
      // Sampled before the swap: removing the focused Back moves focus to <body> and loses this fact.
      backHadFocus.current = card.current?.contains(document.activeElement) ?? false;
      setRemaining(secondsUntil(until));
    }, 1000);
    return () => clearInterval(timer);
  }, [until, over]);

  const visible = over ? t.waitOver : t.waitBody(time, minutesSeconds(remaining));
  return (
    <div ref={card}>
      <EmptyState
        kind="blocked"
        focusTitle
        icon="clock"
        title={t.waitTitle}
        action={
          over ? (
            <Button key="start-again" busy={starting} onClick={onStartAgain}>
              {starting ? t.starting : t.startAgain}
            </Button>
          ) : (
            <Button key="back" className="btn-outline-secondary" onClick={onBack}>
              {t.back}
            </Button>
          )
        }
      >
        <span aria-hidden={!over}>{visible}</span>
      </EmptyState>
    </div>
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
