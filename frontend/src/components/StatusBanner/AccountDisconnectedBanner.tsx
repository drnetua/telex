import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { linkedAccountsKey, useLinkedAccounts } from "../../api/linkedAccounts";
import { useStartLinking } from "../../api/linking";
import { refusalFor } from "../../api/linkingRefusal";
import { messages } from "../../messages";
import { Toast } from "../Toast/Toast";
import { StatusBanner } from "./StatusBanner";

/** AC-122: shown on every signed-in screen while an account has lost its session. */
export function AccountDisconnectedBanner() {
  const accounts = useLinkedAccounts();
  const start = useStartLinking();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [refusal, setRefusal] = useState<string | null>(null);
  const dismissRefusal = useCallback(() => setRefusal(null), []);
  const lost = Array.isArray(accounts.data)
    ? accounts.data.filter((a) => a.state === "session_lost")
    : [];
  const t = messages.banner;
  if (lost.length === 0) return null;
  if (lost.length === 1) {
    const only = lost[0]!;
    return (
      <>
        <StatusBanner
          icon="alert-circle"
          message={t.one(only.displayName)}
          action={t.signInAgain}
          busy={start.isPending}
          onAction={() => {
            setRefusal(null);
            start.mutate(
              { origin: "accounts", targetLinkedAccountId: only.id },
              {
                onSuccess: () => void navigate("/connect-telegram"),
                onError: (error) => {
                  // 404 and "already connected" both mean the list is stale: refetch (AC-03).
                  void client.invalidateQueries({ queryKey: linkedAccountsKey });
                  const message = refusalFor(error);
                  if (message) setTimeout(() => setRefusal(message), 0);
                },
              },
            );
          }}
        />
        {refusal ? <Toast message={refusal} tone="error" onDismiss={dismissRefusal} /> : null}
      </>
    );
  }
  return (
    <StatusBanner
      icon="alert-circle"
      message={t.several(lost.length)}
      action={t.openAccounts}
      onAction={() => void navigate("/accounts")}
    />
  );
}
