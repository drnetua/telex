import { type ReactNode, useEffect, useRef } from "react";
import { Icon, type IconName } from "../Icon/Icon";

interface EmptyStateProps {
  kind: "none" | "blocked" | "first";
  icon: IconName;
  /** Omit when the sentence below is the whole message (SCR-10). */
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
  headingLevel?: 1 | 2;
  /** Moves focus to the title on mount, for a card that replaces what the user was working in. */
  focusTitle?: boolean;
}

export function EmptyState({
  kind,
  icon,
  title,
  children,
  action,
  headingLevel = 1,
  focusTitle = false,
}: EmptyStateProps) {
  const Heading = headingLevel === 1 ? "h1" : "h2";
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (focusTitle) heading.current?.focus();
  }, [focusTitle]);
  return (
    <div className="empty" data-kind={kind}>
      <div className="empty-icon">
        <Icon name={icon} size={40} />
      </div>
      {title ? (
        <Heading className="empty-title" ref={heading} tabIndex={focusTitle ? -1 : undefined}>
          {title}
        </Heading>
      ) : null}
      {children ? <p className="empty-subtitle text-secondary">{children}</p> : null}
      {action ? <div className="empty-action">{action}</div> : null}
    </div>
  );
}
