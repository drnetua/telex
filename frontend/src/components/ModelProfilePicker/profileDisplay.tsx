import type { ModelProfile } from "../../api/models";
import { messages } from "../../messages";
import { formatCost } from "../Cost/formatCost";
import { Icon } from "../Icon/Icon";

const m = messages.models;

/** The icon-and-words warning line, shared by the picker option and the card slot row. */
export function Warning({ children }: { children: string }) {
  return (
    <span className="d-flex align-items-center gap-1 text-warning-emphasis small">
      <Icon name="alert-triangle" size={14} />
      <span>{children}</span>
    </span>
  );
}

/** "≈ $0.30 per 100 runs", "< $0.01 per 100 runs", "Free" or "Price unknown". Nothing without a text model. */
export function PriceLine({ price }: { price: ModelProfile["pricePer100Runs"] }) {
  switch (price.state) {
    case "estimate":
      return (
        <span title={price.amount ?? undefined}>
          {m.pricePer100.estimate(formatCost(price.amount ?? "0", false))}
        </span>
      );
    case "under-one-cent":
      return <span title={price.amount ?? undefined}>{m.pricePer100.underOneCent}</span>;
    case "free":
      return <span>{m.free}</span>;
    case "unknown":
      return <span>{m.priceUnknown}</span>;
    case "no-text-model":
      return null;
  }
}
