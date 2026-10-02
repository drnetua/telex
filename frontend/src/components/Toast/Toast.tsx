import { useEffect } from "react";
import { Icon } from "../Icon/Icon";

interface ToastProps {
  message: string;
  onDismiss: () => void;
  durationMs?: number;
}

/** Info toast: announced politely, dismisses itself. */
export function Toast({ message, onDismiss, durationMs = 5000 }: ToastProps) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [message, onDismiss, durationMs]);
  return (
    <div className="toast-container position-fixed bottom-0 end-0 p-3">
      <div className="toast show" role="status" aria-live="polite">
        <div className="toast-body d-flex align-items-center gap-2">
          <Icon name="info-circle" size={18} />
          {message}
        </div>
      </div>
    </div>
  );
}
