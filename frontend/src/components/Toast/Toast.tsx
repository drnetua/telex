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

let users = 0;

/**
 * One fixed container for every open Toast, so Toasts shown together stack instead of overlapping. It lives for the
 * app's lifetime and is empty, and takes no room, while no Toast is open.
 */
const slot: HTMLElement | null =
  typeof document === "undefined" ? null : document.createElement("div");
/**
 * An empty polite live region inside the container, mounted before any Toast. A live region that arrives together
 * with its text is often not announced; info Toasts are added into this one, so they are. Error Toasts are siblings of
 * it, each its own `role=alert`, so no live region is nested in another.
 */
const politeRegion: HTMLElement | null =
  typeof document === "undefined" ? null : document.createElement("div");
if (slot && politeRegion) {
  slot.className =
    "toast-container telex-toast-container position-fixed bottom-0 end-0 d-flex flex-column gap-2";
  politeRegion.className = "d-flex flex-column gap-2 mb-0";
  politeRegion.setAttribute("aria-live", "polite");
  slot.appendChild(politeRegion);
  document.body.appendChild(slot);
}

function acquireSlot() {
  if (slot && !slot.isConnected) document.body.appendChild(slot);
  slot?.classList.add("p-3");
  users += 1;
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
  if (users === 0) slot?.classList.remove("p-3");
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
    error ? container : (politeRegion ?? container),
  );
}
