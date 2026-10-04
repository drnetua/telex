import { formatCost } from "./formatCost";

/** C-27: US dollars; the exact decimal string is the tooltip. `precision` keeps 2 to 4 decimals (catalog prices). */
export function Cost({ amount, precision = false }: { amount: string; precision?: boolean }) {
  return (
    <span className="text-nowrap" style={{ fontVariantNumeric: "tabular-nums" }} title={amount}>
      {formatCost(amount, precision)}
    </span>
  );
}
