import { useEffect, useId, type ReactNode } from "react";
import { Button } from "../Button/Button";

interface ConfirmDialogProps {
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  busy?: boolean;
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
  tone = "danger",
}: ConfirmDialogProps) {
  const titleId = useId();
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
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
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
                {confirmLabel}
              </Button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop show" />
    </>
  );
}
