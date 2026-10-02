import { useState } from "react";
import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { messages } from "../../messages";

type RetryState = "default" | "retrying" | "retry-failed";

export function UnavailablePage({ onRetry }: { onRetry: () => Promise<unknown> | void }) {
  const [state, setState] = useState<RetryState>("default");

  async function retry() {
    setState("retrying");
    try {
      await onRetry();
    } catch {
      setState("retry-failed");
    }
  }

  const busy = state === "retrying";
  return (
    <EmptyState
      kind="blocked"
      icon="wifi-off"
      title={messages.unavailable.title}
      action={
        <>
          <Button icon="refresh" busy={busy} onClick={() => void retry()}>
            {busy ? messages.unavailable.retrying : messages.unavailable.retry}
          </Button>
          <div aria-live="polite" className="mt-2">
            {state === "retry-failed" ? <small>{messages.unavailable.stillDown}</small> : null}
          </div>
        </>
      }
    >
      {messages.unavailable.body}
    </EmptyState>
  );
}
