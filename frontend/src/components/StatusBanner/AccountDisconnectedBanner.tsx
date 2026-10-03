import { useNavigate } from "react-router";
import { useLinkedAccounts } from "../../api/linkedAccounts";
import { useStartLinking } from "../../api/linking";
import { messages } from "../../messages";
import { StatusBanner } from "./StatusBanner";

/** AC-122: shown on every signed-in screen while an account has lost its session. */
export function AccountDisconnectedBanner() {
  const accounts = useLinkedAccounts();
  const start = useStartLinking();
  const navigate = useNavigate();
  const lost = Array.isArray(accounts.data)
    ? accounts.data.filter((a) => a.state === "session_lost")
    : [];
  const t = messages.banner;
  if (lost.length === 0) return null;
  if (lost.length === 1) {
    const only = lost[0]!;
    return (
      <StatusBanner
        icon="alert-circle"
        message={t.one(only.displayName)}
        action={t.signInAgain}
        busy={start.isPending}
        onAction={() =>
          start.mutate(
            { origin: "accounts", targetLinkedAccountId: only.id },
            { onSuccess: () => void navigate("/connect-telegram") },
          )
        }
      />
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
