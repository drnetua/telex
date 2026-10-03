import { useEffect } from "react";
import { Icon } from "../Icon/Icon";

interface ToastProps {
  message: string;
  onDismiss: () => void;
  durationMs?: number;
  /** Error toasts announce assertively and stay until dismissed. */
  tone?: "info" | "error";
  dismissLabel?: string;
  /** One action (for example "Try again"). A toast with an action stays until dismissed or acted on. */
  action?: { label: string; onClick: () => void };
}

/** Info toast: announced politely, dismisses itself. Error toast: stays until dismissed. */
export function Toast({
  message,
  onDismiss,
  durationMs = 5000,
  tone = "info",
  dismissLabel = "Dismiss",
  action,
}: ToastProps) {
  const error = tone === "error";
  const sticky = error || action !== undefined;
  useEffect(() => {
    if (sticky) return;
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [message, onDismiss, durationMs, sticky]);
  return (
    <div className="toast-container position-fixed bottom-0 end-0 p-3">
      <div
        className="toast show"
        role={error ? "alert" : "status"}
        aria-live={error ? "assertive" : "polite"}
      >
        <div className="toast-body d-flex align-items-center gap-2">
          <Icon name={error ? "alert-circle" : "info-circle"} size={18} />
          {message}
          {action ? (
            <button type="button" className="btn btn-link ms-auto" onClick={action.onClick}>
              {action.label}
            </button>
          ) : null}
          {error ? (
            <button
              type="button"
              className={`btn-close${action ? "" : " ms-auto"}`}
              aria-label={dismissLabel}
              onClick={onDismiss}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
}
