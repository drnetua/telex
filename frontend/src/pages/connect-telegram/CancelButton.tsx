import { Button } from "../../components/Button/Button";
import { messages } from "../../messages";

export function CancelButton({ disabled, onCancel }: { disabled: boolean; onCancel: () => void }) {
  return (
    <Button className="btn-ghost-secondary" disabled={disabled} onClick={onCancel}>
      {messages.linking.cancel}
    </Button>
  );
}
