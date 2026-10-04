import { useEffect, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
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

let slot: HTMLElement | null = null;
let users = 0;

/**
 * One empty polite live region, mounted before any Toast and kept for the app's lifetime. A live region that arrives
 * together with its text is often not announced; info Toasts are added into this one, so they are.
 */
const liveRegion: HTMLElement | null =
  typeof document === "undefined" ? null : document.createElement("div");
if (liveRegion) {
  liveRegion.setAttribute("aria-live", "polite");
  document.body.appendChild(liveRegion);
}

/** One fixed container for every open Toast, so Toasts shown together stack instead of overlapping. */
function acquireSlot(): HTMLElement {
  if (!slot) {
    slot = document.createElement("div");
    slot.className =
      "toast-container telex-toast-container position-fixed bottom-0 end-0 p-3 d-flex flex-column gap-2";
    if (liveRegion && !liveRegion.isConnected) document.body.appendChild(liveRegion);
    (liveRegion ?? document.body).appendChild(slot);
  }
  users += 1;
  return slot;
}

function subscribeToSlot(): () => void {
  acquireSlot();
  return releaseSlot;
}

function currentSlot(): HTMLElement | null {
  return slot;
}

function releaseSlot() {
  users -= 1;
  if (users === 0 && slot) {
    slot.remove();
    slot = null;
  }
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
  const container = useSyncExternalStore(subscribeToSlot, currentSlot, () => null);
  const error = tone === "error";
  const sticky = error || action !== undefined;
  useEffect(() => {
    if (sticky) return;
    const timer = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(timer);
  }, [message, onDismiss, durationMs, sticky]);
  if (!container) return null;
  return createPortal(
    // An info Toast sits inside the persistent polite region, which announces it; its own live attributes would nest.
    <div className="toast show" {...(error ? { role: "alert", "aria-live": "assertive" } : {})}>
      <div className="toast-body d-flex align-items-center gap-2">
        <Icon name={error ? "alert-circle" : "info-circle"} size={18} />
        {message}
        {action ? (
          <button
            type="button"
            className="btn btn-link ms-auto"
            onClick={() => {
              // Acted on: the toast goes away, whatever the caller does with its own state.
              action.onClick();
              onDismiss();
            }}
          >
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
    </div>,
    container,
  );
}
