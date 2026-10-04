import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { linkedAccountsKey, useLinkedAccounts } from "../api/linkedAccounts";
import { linkingAttemptKey, useStartLinking } from "../api/linking";
import { refusalFor } from "../api/linkingRefusal";
import { Toast } from "../components/Toast/Toast";
import { messages } from "../messages";
import type { ConditionLive } from "./conditions";
import { PULSE_KEY } from "./pulse";

/**
 * AC-122: what the shell's `account-disconnected` condition says and offers. One Session lost account is named and
 * signed in again against its target (AC-117); several open Accounts. Until the list arrives the catalog default stands.
 */
export function useAccountDisconnected(): ConditionLive {
  const accounts = useLinkedAccounts();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [refusal, setRefusal] = useState<string | null>(null);
  // The outcome handlers live on the mutation, not on a click: the banner line may be gone when the request ends.
  const start = useStartLinking({
    onSuccess: (attempt) => {
      // The wizard shows this attempt at once, also when it is already open on an outcome card.
      client.setQueryData(linkingAttemptKey, attempt);
      void client.invalidateQueries({ queryKey: PULSE_KEY });
      void navigate("/connect-telegram");
    },
    onError: (error) => {
      // 404 and "already connected" both mean the list is stale: refetch (AC-03).
      void client.invalidateQueries({ queryKey: linkedAccountsKey });
      const message = refusalFor(error);
      if (message) setTimeout(() => setRefusal(message), 0);
    },
  });
  const dismissRefusal = useCallback(() => setRefusal(null), []);
  const t = messages.banner;
  const notice = refusal ? (
    <Toast message={refusal} tone="error" onDismiss={dismissRefusal} />
  ) : undefined;
  // Only while the list is unknown does the catalog default stand; a loaded list outranks the pulse.
  if (!Array.isArray(accounts.data)) return { notice };
  const lost = accounts.data.filter((a) => a.state === "session_lost");
  if (lost.length === 0) return { inactive: true, notice };
  if (lost.length > 1) {
    return {
      message: t.several(lost.length),
      action: { kind: "button", label: t.openAccounts, onClick: () => void navigate("/accounts") },
      notice,
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
        start.mutate({ origin: "accounts", targetLinkedAccountId: only.id });
      },
    },
    notice,
  };
}
