import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { linkedAccountsKey, useLinkedAccounts } from "../api/linkedAccounts";
import { linkingAttemptKey, useStartLinking } from "../api/linking";
import { refusalFor } from "../api/linkingRefusal";
import { Toast } from "../components/Toast/Toast";
import { messages } from "../messages";
import type { ConditionLive } from "./conditions";

/**
 * AC-122: what the shell's `account-disconnected` condition says and offers. One Session lost account is named and
 * signed in again against its target (AC-117); several open Accounts. Until the list arrives the catalog default stands.
 */
export function useAccountDisconnected(): ConditionLive {
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
  if (lost.length === 0) return {};
  if (lost.length > 1) {
    return {
      message: t.several(lost.length),
      action: { kind: "button", label: t.openAccounts, onClick: () => void navigate("/accounts") },
    };
  }
  const only = lost[0]!;
  return {
    message: t.one(only.displayName),
    action: {
      kind: "button",
      label: t.signInAgain,
      busy: start.isPending,
      onClick: () => {
        setRefusal(null);
        start.mutate(
          { origin: "accounts", targetLinkedAccountId: only.id },
          {
            onSuccess: (attempt) => {
              // The wizard shows this attempt at once, also when it is already open on an outcome card.
              client.setQueryData(linkingAttemptKey, attempt);
              void navigate("/connect-telegram");
            },
            onError: (error) => {
              // 404 and "already connected" both mean the list is stale: refetch (AC-03).
              void client.invalidateQueries({ queryKey: linkedAccountsKey });
              const message = refusalFor(error);
              if (message) setTimeout(() => setRefusal(message), 0);
            },
          },
        );
      },
    },
    notice: refusal ? (
      <Toast message={refusal} tone="error" onDismiss={dismissRefusal} />
    ) : undefined,
  };
}
