export function formatCost(amount: string, precision: boolean): string {
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
