import { useState, useSyncExternalStore, type ReactNode } from "react";
import { Link } from "react-router";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { connectivity, retryNow } from "../connectivity";
import { orderConditions, type Condition, type ConditionAction } from "../conditions";

const m = messages.shell.banner;

function Action({
  action,
  busy,
  onRetry,
}: {
  action: ConditionAction;
  busy: boolean;
  onRetry: () => void;
}) {
  if (action.kind === "link") {
    return (
      <Link to={action.to} className="btn btn-sm btn-secondary">
        {action.label}
      </Link>
    );
  }
  if (action.kind === "button") {
    return (
      <Button className="btn-secondary btn-sm" busy={action.busy} onClick={action.onClick}>
        {action.label}
      </Button>
    );
  }
  return (
    <Button className="btn-secondary btn-sm" busy={busy} onClick={onRetry}>
      {busy ? m.tryingAgain : m.tryAgain}
    </Button>
  );
}

/** One condition's line; it is keyed by code, so a condition's live hook always runs in its own instance. */
function ConditionLine({
  condition,
  as: Tag,
  busy,
  stillDown,
  onRetry,
  children,
}: {
  condition: Condition;
  as: "div" | "li";
  busy: boolean;
  stillDown: boolean;
  onRetry: () => void;
  children?: ReactNode;
}) {
  const live = condition.useLive?.() ?? {};
  const action = live.action ?? condition.action;
  // The still-down text belongs to a connectivity failure that survived a retry.
  const text =
    action.kind === "retry" && stillDown ? m.stillDown : (live.message ?? condition.message);
  return (
    <Tag className="d-flex align-items-center gap-2 flex-wrap">
      <Icon name={condition.icon} size={20} />
      <span className="flex-grow-1">{text}</span>
      {children}
      <Action action={action} busy={busy} onRetry={onRetry} />
      {live.notice}
    </Tag>
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

  return (
    <div role="status">
      <div className="alert alert-warning rounded-0 mb-0 border-0">
        <ConditionLine
          key={top.code}
          condition={top}
          as="div"
          busy={busy}
          stillDown={stillDown}
          onRetry={onRetry}
        >
          {rest.length > 0 ? (
            <Button
              className="btn-link"
              aria-expanded={expanded}
              onClick={() => setExpanded((v) => !v)}
            >
              {m.more(rest.length)}
            </Button>
          ) : null}
        </ConditionLine>
        {expanded && rest.length > 0 ? (
          <ul className="list-unstyled mb-0 mt-2 d-flex flex-column gap-2">
            {rest.map((condition) => (
              <ConditionLine
                key={condition.code}
                condition={condition}
                as="li"
                busy={busy}
                stillDown={false}
                onRetry={onRetry}
              />
            ))}
          </ul>
        ) : null}
      </div>
    </div>
  );
}
