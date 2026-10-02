import {
  useEffect,
  useId,
  useRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { Button } from "../Button/Button";

interface ConfirmDialogProps {
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
  /** Confirm label while busy (defaults to `confirmLabel`). */
  busyLabel?: string;
  /** Confirm button styling: `danger` for destructive actions. */
  tone?: "default" | "danger";
}

/** Modal confirmation that names the consequence. */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  busy = false,
  busyLabel,
  tone = "danger",
}: ConfirmDialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  // Move focus into the dialog on open and give it back to the opener on close.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    dialogRef.current?.querySelector<HTMLElement>("button:not(:disabled)")?.focus();
    return () => opener?.focus?.();
  }, []);

  function trapTab(e: ReactKeyboardEvent) {
    if (e.key !== "Tab") return;
    const buttons = Array.from(
      dialogRef.current?.querySelectorAll<HTMLElement>("button:not(:disabled)") ?? [],
    );
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (!first || !last) return;
    const active = document.activeElement;
    if (e.shiftKey && active === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && active === last) {
      e.preventDefault();
      first.focus();
    }
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !busy) onCancel();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [busy, onCancel]);
  return (
    <>
      <div
        className="modal modal-blur d-block"
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={trapTab}
      >
        <div className="modal-dialog modal-sm modal-dialog-centered">
          <div className="modal-content">
            <div className="modal-body">
              <h2 id={titleId} className="h3">
                {title}
              </h2>
              <p className="text-secondary mb-0">{children}</p>
            </div>
            <div className="modal-footer">
              <Button className="btn-ghost-secondary" disabled={busy} onClick={onCancel}>
                {cancelLabel}
              </Button>
              <Button
                className={tone === "danger" ? "btn-danger" : "btn-primary"}
                busy={busy}
                onClick={onConfirm}
              >
                {busy && busyLabel ? busyLabel : confirmLabel}
              </Button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
