import type { ButtonHTMLAttributes } from "react";
import { Icon, type IconName } from "../Icon/Icon";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon?: IconName;
  /** Disabled with a spinner, keeps its label. */
  busy?: boolean;
}

export function Button({
  icon,
  busy = false,
  children,
  className = "btn-primary",
  ...rest
}: ButtonProps) {
  return (
    <button
      type="button"
      className={`btn ${className}`}
      {...rest}
      disabled={busy || rest.disabled}
      aria-busy={busy}
    >
      {busy ? <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" /> : null}
      {!busy && icon ? <Icon name={icon} size={18} /> : null}
      {children}
    </button>
  );
}
