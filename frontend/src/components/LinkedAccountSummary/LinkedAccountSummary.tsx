import type { ReactNode } from "react";
import { Link } from "react-router";
import { formatMaskedPhone, type LinkedAccount } from "../../api/linkedAccounts";
import { messages } from "../../messages";
import { Badge } from "../Badge/Badge";

interface LinkedAccountSummaryProps {
  account: LinkedAccount;
  /** `line`: compact, whole row links to Accounts (SCR-10). `row`: with an actions slot (SCR-60). */
  variant: "line" | "row";
  actions?: ReactNode;
}

function StateBadge({ state }: { state: LinkedAccount["state"] }) {
  const t = messages.accounts;
  if (state === "reconnecting") {
    return (
      <Badge tone="neutral" icon="refresh">
        {t.reconnecting}
      </Badge>
    );
  }
  if (state === "session_lost") {
    return (
      <Badge tone="danger" icon="alert-circle">
        {t.sessionLost}
      </Badge>
    );
  }
  return (
    <Badge tone="success" icon="check">
      {t.connected}
    </Badge>
  );
}

function SyncLine({ account }: { account: LinkedAccount }) {
  const t = messages.accounts;
  const { chatsSynced, chatsTotal, completedAt } = account.chatSync;
  if (account.state !== "connected") return null;
  if (completedAt !== null) {
    return <div className="small text-secondary">{t.chats(chatsSynced)}</div>;
  }
  const label = chatsTotal === null ? t.syncingUnknown : t.syncing(chatsSynced, chatsTotal);
  const percent = chatsTotal ? Math.round((chatsSynced / chatsTotal) * 100) : 0;
  return (
    <div>
      <div className="progress progress-sm my-1">
        {chatsTotal === null ? (
          <div
            className="progress-bar progress-bar-indeterminate"
            role="progressbar"
            aria-label={label}
          />
        ) : (
          <div
            className="progress-bar"
            role="progressbar"
            aria-label={label}
            aria-valuemin={0}
            aria-valuemax={chatsTotal}
            aria-valuenow={chatsSynced}
            style={{ width: `${percent}%` }}
          />
        )}
      </div>
      <div className="small text-secondary">{label}</div>
    </div>
  );
}

export function LinkedAccountSummary({ account, variant, actions }: LinkedAccountSummaryProps) {
  const body = (
    <div className="d-flex flex-wrap align-items-start justify-content-between gap-2 w-100">
      <div className="flex-grow-1">
        <div className="fw-medium">{account.displayName}</div>
        <div className="small text-secondary">{formatMaskedPhone(account.phone)}</div>
        <SyncLine account={account} />
      </div>
      <div className="d-flex align-items-center gap-2">
        <span aria-live="polite">
          <StateBadge state={account.state} />
        </span>
        {variant === "row" ? actions : null}
      </div>
    </div>
  );
  if (variant === "line") {
    return (
      <Link to="/accounts" className="d-flex text-reset text-decoration-none py-2 touch-target">
        {body}
      </Link>
    );
  }
  return <div className="d-flex py-2">{body}</div>;
}
