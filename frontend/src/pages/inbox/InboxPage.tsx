import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { useLinkedAccounts } from "../../api/linkedAccounts";
import { linkingAttemptKey, useStartLinking } from "../../api/linking";
import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { LinkedAccountSummary } from "../../components/LinkedAccountSummary/LinkedAccountSummary";
import { LoadState } from "../../components/LoadState/LoadState";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";

export function InboxPage() {
  const accounts = useLinkedAccounts();
  const start = useStartLinking();
  const client = useQueryClient();
  const navigate = useNavigate();
  const text = messages.inbox;
  const [refusal, setRefusal] = useState<string | null>(null);
  const dismissRefusal = useCallback(() => setRefusal(null), []);
  const arrival = (useLocation().state as { toast?: string } | null)?.toast;
  const [arrivalShown, setArrivalShown] = useState(true);
  const dismissArrival = useCallback(() => setArrivalShown(false), []);

  const connect = () => {
    setRefusal(null);
    start.mutate(
      { origin: "inbox" },
      {
        onSuccess: (attempt) => {
          client.setQueryData(linkingAttemptKey, attempt);
          void navigate("/connect-telegram");
        },
        onError: (error) => {
          const problems: Record<string, unknown> = messages.linking.problems;
          const message = error instanceof ApiFailure ? problems[error.code] : undefined;
          if (typeof message === "string") setTimeout(() => setRefusal(message), 0);
        },
      },
    );
  };

  if (!accounts.data) return <LoadState state="loading" />;
  return (
    <>
      <h1 className="page-title mb-4">{text.title}</h1>
      {accounts.data.length === 0 ? (
        <EmptyState
          kind="first"
          icon="brand-telegram"
          action={
            <Button busy={start.isPending} onClick={connect}>
              {start.isPending ? messages.linking.starting : text.connect}
            </Button>
          }
        >
          {text.empty}
        </EmptyState>
      ) : (
        <div className="card">
          <div className="list-group list-group-flush">
            {accounts.data.map((account) => (
              <div className="list-group-item" key={account.id}>
                <LinkedAccountSummary account={account} variant="line" />
              </div>
            ))}
          </div>
        </div>
      )}
      {arrival && arrivalShown ? <Toast message={arrival} onDismiss={dismissArrival} /> : null}
      {refusal ? <Toast message={refusal} tone="error" onDismiss={dismissRefusal} /> : null}
    </>
  );
}
