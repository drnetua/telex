import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { refusalFor } from "../../api/linkingRefusal";
import {
  linkedAccountsKey,
  useLinkedAccounts,
  useUnlinkAccount,
  type LinkedAccount,
} from "../../api/linkedAccounts";
import { linkingAttemptKey, useStartLinking } from "../../api/linking";
import { Button } from "../../components/Button/Button";
import { ConfirmDialog } from "../../components/ConfirmDialog/ConfirmDialog";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { LinkedAccountSummary } from "../../components/LinkedAccountSummary/LinkedAccountSummary";
import { LoadState } from "../../components/LoadState/LoadState";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { useArrivalToast } from "../useArrivalToast";

interface Notice {
  message: string;
  tone: "info" | "error";
}

export function AccountsPage() {
  const accounts = useLinkedAccounts();
  const start = useStartLinking();
  const unlink = useUnlinkAccount();
  const client = useQueryClient();
  const navigate = useNavigate();
  const text = messages.accounts;
  const [starting, setStarting] = useState<string | "new" | null>(null);
  const [target, setTarget] = useState<LinkedAccount | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const dismissNotice = useCallback(() => setNotice(null), []);
  const cancelUnlink = useCallback(() => setTarget(null), []);
  const { arrival, dismiss: dismissArrival } = useArrivalToast();
  const heading = useRef<HTMLHeadingElement>(null);
  const unlinked = useRef(false);

  const begin = (targetLinkedAccountId?: string) => {
    setNotice(null);
    setStarting(targetLinkedAccountId ?? "new");
    start.mutate(
      targetLinkedAccountId
        ? { origin: "accounts", targetLinkedAccountId }
        : { origin: "accounts" },
      {
        onSuccess: (attempt) => {
          client.setQueryData(linkingAttemptKey, attempt);
          void navigate("/connect-telegram");
        },
        onError: (error) => {
          setStarting(null);
          // 404 and "already connected" both mean the list is stale: refetch (AC-03).
          void client.invalidateQueries({ queryKey: linkedAccountsKey });
          const message = refusalFor(error);
          if (message) setTimeout(() => setNotice({ message, tone: "error" }), 0);
        },
      },
    );
  };

  const confirmUnlink = () => {
    if (!target) return;
    const gone = target;
    const remaining = (accounts.data?.length ?? 1) - 1;
    unlink.mutate(gone.id, {
      onSuccess: (result) => {
        unlinked.current = true;
        setTarget(null);
        const next: Notice = result.signOutConfirmed
          ? { message: text.unlinked(gone.displayName), tone: "info" }
          : { message: text.unlinkedUnconfirmed(gone.displayName), tone: "error" };
        if (remaining <= 0)
          void navigate("/inbox", { state: { toast: next.message, tone: next.tone } });
        else setNotice(next);
      },
      onError: (error) => {
        if (error instanceof ApiFailure && error.status === 404) setTarget(null);
      },
    });
  };

  if (!accounts.data) return <LoadState state="loading" />;
  const busyStart = start.isPending;
  return (
    <>
      <h1 className="page-title mb-1" tabIndex={-1} ref={heading}>
        {text.title}
      </h1>
      <p className="text-secondary mb-4">{text.intro}</p>
      {accounts.data.length === 0 ? (
        <EmptyState
          kind="first"
          icon="brand-telegram"
          headingLevel={2}
          action={
            <Button busy={busyStart} onClick={() => begin()}>
              {busyStart ? text.starting : text.add}
            </Button>
          }
        >
          {text.emptyTitle}
        </EmptyState>
      ) : (
        <div className="card">
          <div className="card-header d-flex align-items-center justify-content-between">
            <h2 className="card-title">{text.cardTitle}</h2>
            <Button
              className="btn-outline-secondary"
              icon="plus"
              busy={busyStart && starting === "new"}
              disabled={busyStart}
              onClick={() => begin()}
            >
              {busyStart && starting === "new" ? text.starting : text.add}
            </Button>
          </div>
          <div className="list-group list-group-flush">
            {accounts.data.map((account) => (
              <div className="list-group-item" key={account.id}>
                <LinkedAccountSummary
                  account={account}
                  variant="row"
                  actions={
                    <div className="d-flex flex-column flex-sm-row gap-2">
                      {account.state === "session_lost" ? (
                        <Button
                          className="btn-primary btn-sm"
                          busy={busyStart && starting === account.id}
                          disabled={busyStart}
                          onClick={() => begin(account.id)}
                        >
                          {busyStart && starting === account.id ? text.starting : text.signInAgain}
                        </Button>
                      ) : null}
                      <Button
                        className="btn-ghost-secondary btn-sm"
                        icon="unlink"
                        onClick={() => setTarget(account)}
                      >
                        {text.unlink}
                      </Button>
                    </div>
                  }
                />
              </div>
            ))}
          </div>
        </div>
      )}
      {target ? (
        <ConfirmDialog
          title={text.unlinkTitle(target.displayName)}
          confirmLabel={text.unlinkConfirm}
          cancelLabel={text.unlinkKeep}
          busyLabel={text.unlinking}
          busy={unlink.isPending}
          returnFocusTo={() => (unlinked.current ? heading.current : null)}
          onConfirm={confirmUnlink}
          onCancel={cancelUnlink}
        >
          {text.unlinkBody(target.chatSync.chatsSynced)}
        </ConfirmDialog>
      ) : null}
      {arrival ? (
        <Toast message={arrival.message} tone={arrival.tone} onDismiss={dismissArrival} />
      ) : null}
      {notice ? (
        <Toast message={notice.message} tone={notice.tone} onDismiss={dismissNotice} />
      ) : null}
    </>
  );
}
