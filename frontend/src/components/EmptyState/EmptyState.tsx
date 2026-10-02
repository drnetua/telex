import type { ReactNode } from "react";
import { Icon, type IconName } from "../Icon/Icon";

interface EmptyStateProps {
  kind: "none" | "blocked";
  icon: IconName;
  title: string;
  children: ReactNode;
  action?: ReactNode;
}

export function EmptyState({ kind, icon, title, children, action }: EmptyStateProps) {
  return (
    <div className="empty" data-kind={kind}>
      <div className="empty-icon">
        <Icon name={icon} size={40} />
      </div>
      <h1 className="empty-title">{title}</h1>
      <p className="empty-subtitle text-secondary">{children}</p>
      {action ? <div className="empty-action">{action}</div> : null}
    </div>
  );
}
