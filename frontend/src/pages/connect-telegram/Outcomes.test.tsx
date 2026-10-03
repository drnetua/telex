import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RefusedState, WaitState } from "./Outcomes";

describe("SCR-02 wait state", () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date("2026-01-01T10:00:00") }));
  afterEach(() => vi.useRealTimers());

  it("counts down every second and offers Start again at zero", () => {
    render(
      <WaitState
        retryAt="2026-01-01T10:01:05"
        starting={false}
        onBack={vi.fn()}
        onStartAgain={vi.fn()}
      />,
    );
    expect(screen.getByRole("heading", { name: "Too many attempts" })).toBeVisible();
    expect(
      screen.getByText("Telegram asks you to wait. You can try again at 10:01, in 1:05.", {
        selector: "[aria-hidden='true']",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Start again" })).not.toBeInTheDocument();

    act(() => void vi.advanceTimersByTime(1000));
    expect(
      screen.getByText("Telegram asks you to wait. You can try again at 10:01, in 1:04.", {
        selector: "[aria-hidden='true']",
      }),
    ).toBeInTheDocument();
    // announced at the start only, not on every tick
    expect(screen.getByRole("status")).toHaveTextContent(/in 1:05\.$/);

    act(() => void vi.advanceTimersByTime(64_000));
    expect(screen.getAllByText("You can try again now.")).toHaveLength(2);
    expect(screen.getByRole("status")).toHaveTextContent("You can try again now.");
    expect(screen.getByRole("button", { name: "Start again" })).toBeEnabled();
  });
});

describe("screens.md component fixes", () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date("2026-01-01T10:00:00") }));
  afterEach(() => vi.useRealTimers());

  it("S11: the wait-state Back button is secondary, not ghost", () => {
    render(
      <WaitState
        retryAt="2026-01-01T10:01:05"
        starting={false}
        onBack={vi.fn()}
        onStartAgain={vi.fn()}
      />,
    );
    const back = screen.getByRole("button", { name: "Back" });
    expect(back).toHaveClass("btn-outline-secondary");
    expect(back).not.toHaveClass("btn-ghost-secondary");
  });

  it("S11: refused-mismatch uses the ban icon", () => {
    const { container } = render(
      <RefusedState
        code="telegram-account-mismatch"
        displayName="Work"
        onBack={vi.fn()}
        onOpenAccounts={vi.fn()}
      />,
    );
    expect(container.querySelector(".tabler-icon-ban")).not.toBeNull();
    expect(container.querySelector(".tabler-icon-alert-circle")).toBeNull();
  });
});
