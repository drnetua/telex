import { useCallback, useState } from "react";
import { useMe } from "../../api/account";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { LoadState } from "../../components/LoadState/LoadState";
import { Button } from "../../components/Button/Button";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";

export function InboxPage() {
  const me = useMe();
  const [toastShown, setToastShown] = useState(false);
  const dismiss = useCallback(() => setToastShown(false), []);
  const text = messages.inbox;

  if (!me.data) return <LoadState state="loading" />;
  return (
    <>
      <h1 className="page-title mb-4">{text.title}</h1>
      <EmptyState
        kind="first"
        icon="brand-telegram"
        action={
          <Button
            onClick={() => {
              setToastShown(false);
              setTimeout(() => setToastShown(true), 0);
            }}
          >
            {text.connect}
          </Button>
        }
      >
        {text.empty}
      </EmptyState>
      {toastShown ? <Toast message={text.note} onDismiss={dismiss} /> : null}
    </>
  );
}
