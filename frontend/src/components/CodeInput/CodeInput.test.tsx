import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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

  it("keeps its digits while read-only: paste, Backspace and typing change nothing", async () => {
    const onChange = vi.fn();
    render(<CodeInput value="12" onChange={onChange} readOnly />);
    const digits = screen.getAllByRole("textbox");
    fireEvent.paste(digits[0]!, { clipboardData: { getData: () => "654321" } });
    await userEvent.click(digits[3]!);
    await userEvent.keyboard("{Backspace}");
    await userEvent.type(digits[2]!, "7");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("takes a pasted code and steps back on Backspace while editable", async () => {
    const onChange = vi.fn();
    render(<CodeInput value="123" onChange={onChange} />);
    const digits = screen.getAllByRole("textbox");
    fireEvent.paste(digits[0]!, { clipboardData: { getData: () => "654321" } });
    expect(onChange).toHaveBeenLastCalledWith("654321");
    await userEvent.click(digits[3]!);
    await userEvent.keyboard("{Backspace}");
    expect(onChange).toHaveBeenLastCalledWith("12");
  });
});
