import type { ReactNode } from "react";
import { Icon, type IconName } from "../Icon/Icon";

interface EmptyStateProps {
  kind: "none" | "blocked" | "first";
  icon: IconName;
  title: string;
  children: ReactNode;
  action?: ReactNode;
  headingLevel?: 1 | 2;
}

export function EmptyState({
  kind,
  icon,
  title,
  children,
  action,
  headingLevel = 1,
}: EmptyStateProps) {
  const Heading = headingLevel === 1 ? "h1" : "h2";
  return (
    <div className="empty" data-kind={kind}>
      <div className="empty-icon">
        <Icon name={icon} size={40} />
      </div>
      <Heading className="empty-title">{title}</Heading>
      <p className="empty-subtitle text-secondary">{children}</p>
      {action ? <div className="empty-action">{action}</div> : null}
    </div>
  );
}
