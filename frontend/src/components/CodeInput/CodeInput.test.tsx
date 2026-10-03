import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CodeInput } from "./CodeInput";

describe("CodeInput", () => {
  it("defaults to six digits with the E01 label", () => {
    render(<CodeInput value="" onChange={() => {}} />);
    expect(screen.getAllByRole("textbox")).toHaveLength(6);
    expect(screen.getByRole("group", { name: "Sign-in code" })).toBeInTheDocument();
  });

  it("takes a length and a label", () => {
    render(<CodeInput value="12" onChange={() => {}} length={5} label="Telegram code" />);
    expect(screen.getAllByRole("textbox")).toHaveLength(5);
    expect(screen.getByRole("group", { name: "Telegram code" })).toBeInTheDocument();
  });
});
