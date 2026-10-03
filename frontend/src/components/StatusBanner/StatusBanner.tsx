import { messages } from "../../messages";
import { Button } from "../Button/Button";
import { Icon, type IconName } from "../Icon/Icon";

interface StatusBannerProps {
  icon: IconName;
  message: string;
  action: string;
  onAction: () => void;
  busy?: boolean;
  /** Other conditions beyond the most severe one shown. */
  more?: number;
}

/** C-04: one system-wide condition, one action, no close button while its cause holds. */
export function StatusBanner({
  icon,
  message,
  action,
  onAction,
  busy,
  more = 0,
}: StatusBannerProps) {
  return (
    <div
      role="status"
      className="alert alert-danger d-flex flex-wrap align-items-center gap-2 mb-0 rounded-0"
    >
      <Icon name={icon} size={20} />
      <span className="flex-grow-1">
        {message}
        {more > 0 ? <span className="ms-2 fw-medium">{messages.banner.more(more)}</span> : null}
      </span>
      <Button className="btn-outline-danger touch-target" busy={busy} onClick={onAction}>
        {action}
      </Button>
    </div>
  );
}
