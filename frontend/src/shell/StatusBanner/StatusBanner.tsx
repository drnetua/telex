import { useState, useSyncExternalStore } from "react";
import { Link } from "react-router";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { connectivity, retryNow } from "../connectivity";
import { orderConditions, type Condition } from "../conditions";

const m = messages.shell.banner;

function Action({
  condition,
  busy,
  onRetry,
}: {
  condition: Condition;
  busy: boolean;
  onRetry: () => void;
}) {
  const { action } = condition;
  if (action.kind === "link") {
    return (
      <Link to={action.to} className="btn btn-sm btn-secondary">
        {action.label}
      </Link>
    );
  }
  return (
    <Button className="btn-secondary btn-sm" busy={busy} onClick={onRetry}>
      {busy ? m.tryingAgain : m.tryAgain}
    </Button>
  );
}

interface StatusBannerProps {
  /** Server-reported condition codes from the pulse; unknown codes are ignored. */
  conditions: readonly string[];
}

/** Status Banner (C-04): the most important condition, the rest under "+N more"; never dismissable. */
export function StatusBanner({ conditions }: StatusBannerProps) {
  const link = useSyncExternalStore(connectivity.subscribe, connectivity.get, connectivity.get);
  const [busy, setBusy] = useState(false);
  const [stillDown, setStillDown] = useState(false);
  const [expanded, setExpanded] = useState(false);

  if (link === "online" && stillDown) setStillDown(false);

  const codes = link === "online" ? conditions : [link, ...conditions];
  const ordered = orderConditions(codes);
  const [top, ...rest] = ordered;
  if (!top) return <div role="status" />;

  const onRetry = () => {
    setBusy(true);
    setStillDown(false);
    void retryNow()
      .then((result) => setStillDown(result.stillDown))
      .finally(() => setBusy(false));
  };

  // The still-down text belongs to a connectivity failure that survived a retry.
  const text =
    link !== "online" && top.action.kind === "retry" && stillDown ? m.stillDown : top.message;

  return (
    <div role="status">
      <div className="alert alert-warning rounded-0 mb-0 border-0">
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <Icon name={top.icon} size={20} />
          <span className="flex-grow-1">{text}</span>
          {rest.length > 0 ? (
            <Button
              className="btn-link"
              aria-expanded={expanded}
              onClick={() => setExpanded((v) => !v)}
            >
              {m.more(rest.length)}
            </Button>
          ) : null}
          <Action condition={top} busy={busy} onRetry={onRetry} />
        </div>
        {expanded && rest.length > 0 ? (
          <ul className="list-unstyled mb-0 mt-2 d-flex flex-column gap-2">
            {rest.map((condition) => (
              <li key={condition.code} className="d-flex align-items-center gap-2 flex-wrap">
                <Icon name={condition.icon} size={20} />
                <span className="flex-grow-1">{condition.message}</span>
                <Action condition={condition} busy={busy} onRetry={onRetry} />
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </div>
  );
}
