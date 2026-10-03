import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InboxCounter } from "./InboxCounter";

const pill = () => document.querySelector(".badge");

describe("InboxCounter (AC-174, AC-175)", () => {
  it("shows no number at 0", () => {
    render(<InboxCounter count={0} />);
    expect(pill()).toBeNull();
  });

  it("shows no number before the first pulse", () => {
    render(<InboxCounter count={undefined} />);
    expect(pill()).toBeNull();
  });

  it("shows the count with its label for 1 to 99", () => {
    const { rerender } = render(<InboxCounter count={3} />);
    expect(pill()).toHaveTextContent("3");
    expect(screen.getByLabelText("3 items need you")).toBe(pill());
    rerender(<InboxCounter count={99} />);
    expect(pill()).toHaveTextContent("99");
    expect(pill()).not.toHaveTextContent("99+");
  });

  it("shows 99+ above 99", () => {
    render(<InboxCounter count={100} />);
    expect(pill()).toHaveTextContent("99+");
    expect(screen.getByLabelText("More than 99 items need you")).toBe(pill());
  });

  it("updates without remount", () => {
    const { rerender } = render(<InboxCounter count={3} />);
    const before = pill();
    rerender(<InboxCounter count={4} />);
    expect(pill()).toBe(before);
    expect(pill()).toHaveTextContent("4");
    rerender(<InboxCounter count={0} />);
    expect(pill()).toBeNull();
  });

  it("announces each change once, politely", () => {
    const { rerender } = render(<InboxCounter count={3} />);
    const live = screen.getByRole("status");
    expect(live).toHaveAttribute("aria-live", "polite");
    rerender(<InboxCounter count={4} />);
    expect(live).toHaveTextContent("4 items need you");
    const node = live.firstChild;
    rerender(<InboxCounter count={4} />);
    expect(live.firstChild).toBe(node);
    expect(live).toHaveTextContent("4 items need you");
    rerender(<InboxCounter count={0} />);
    expect(live).toHaveTextContent("No items need you");
  });
});
