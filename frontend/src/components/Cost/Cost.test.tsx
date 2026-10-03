import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Cost } from "./Cost";

describe("Cost (C-27) with precision", () => {
  it("rounds to the cent by default, with the exact value in the tooltip", () => {
    render(<Cost amount="0.0412" />);
    expect(screen.getByText("$0.04")).toHaveAttribute("title", "0.0412");
  });

  it("shows '< $0.01' below a cent", () => {
    render(<Cost amount="0.004" />);
    expect(screen.getByText("< $0.01")).toHaveAttribute("title", "0.004");
  });

  it.each([
    ["2.5", "$2.50"],
    ["10", "$10.00"],
    ["0.075", "$0.075"],
    ["0.0004", "$0.0004"],
    ["0.00012345", "$0.0001"],
  ])(
    "precision: %s shows as %s (2 to 4 decimals) with the exact string in the tooltip",
    (amount, text) => {
      render(<Cost amount={amount} precision />);
      expect(screen.getByText(text)).toHaveAttribute("title", amount);
    },
  );
});
