import type { ReactNode } from "react";
import { Icon, type IconName } from "../Icon/Icon";

interface BadgeProps {
  tone?: "neutral" | "success" | "danger";
  icon?: IconName;
  children: ReactNode;
}

/** Status is always icon plus words, never colour alone. */
export function Badge({ tone = "neutral", icon, children }: BadgeProps) {
  const cls = tone === "neutral" ? "bg-secondary-lt" : `bg-${tone}-lt`;
  return (
    <span className={`badge ${cls} d-inline-flex align-items-center gap-1`}>
      {icon ? <Icon name={icon} size={14} /> : null}
      {children}
    </span>
  );
}
