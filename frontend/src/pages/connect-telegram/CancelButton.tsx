import { useState } from "react";
import { Button } from "../../components/Button/Button";
import { messages } from "../../messages";

interface CancelButtonProps {
  disabled: boolean;
  onCancel: () => Promise<void> | void;
}

export function CancelButton({ disabled, onCancel }: CancelButtonProps) {
  const [cancelling, setCancelling] = useState(false);
  const cancel = async () => {
    setCancelling(true);
    try {
      await onCancel();
    } finally {
      setCancelling(false);
    }
  };
  return (
    <Button
      className="btn-ghost-secondary"
      busy={cancelling}
      disabled={disabled}
      onClick={() => void cancel()}
    >
      {cancelling ? messages.linking.cancelling : messages.linking.cancel}
    </Button>
  );
}
