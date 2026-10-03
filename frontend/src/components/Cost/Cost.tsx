/** C-27: US dollars; the exact decimal string is the tooltip. `precision` keeps 2 to 4 decimals (catalog prices). */
export function Cost({ amount, precision = false }: { amount: string; precision?: boolean }) {
  return (
    <span className="text-nowrap" style={{ fontVariantNumeric: "tabular-nums" }} title={amount}>
      {format(amount, precision)}
    </span>
  );
}

function format(amount: string, precision: boolean): string {
  const value = Number(amount);
  if (precision) {
    if (value > 0 && value < 0.0001) return "< $0.0001";
    return `$${value
      .toFixed(4)
      .replace(/(\.\d\d)0{1,2}$/, "$1")
      .replace(/(\.\d\d\d)0$/, "$1")}`;
  }
  if (value > 0 && value < 0.01) return "< $0.01";
  return `$${value.toFixed(2)}`;
}
