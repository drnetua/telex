import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import type { IconName } from "../../components/Icon/Icon";
import { messages } from "../../messages";

interface RefusalCardProps {
  icon: IconName;
  title: string;
  body: string;
  busy?: boolean;
  onResend: () => void;
}

/** Shared refusal card for SCR-07 and SCR-08: why sign-in was refused plus "Send a new link". */
export function RefusalCard({ icon, title, body, busy = false, onResend }: RefusalCardProps) {
  return (
    <EmptyState
      kind="blocked"
      icon={icon}
      title={title}
      action={
        <Button busy={busy} onClick={onResend}>
          {busy ? messages.checkEmail.resending : messages.checkEmail.resend}
        </Button>
      }
    >
      {body}
    </EmptyState>
  );
}
