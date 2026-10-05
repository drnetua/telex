import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import { type ReactNode, useLayoutEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { meKey } from "../../api/account";
import { RefusedState, WaitState } from "./Outcomes";

/** Records the live region's text as the first commit leaves it, before any passive effect runs. */
function FirstCommit({ seen, children }: { seen: string[]; children: ReactNode }) {
  useLayoutEffect(() => {
    if (seen.length === 0) {
      seen.push(document.querySelector("[role='status']")?.textContent ?? "no live region");
    }
  });
  return children;
}

/** The wait card reads the Owner's zone; none saved here, so it shows the device's. */
function renderWait(retryAt: string, firstCommit: string[] = []) {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } });
  client.setQueryData(meKey, { ownerId: "o1", email: "ann@example.com", timeZone: null });
  return render(
    <QueryClientProvider client={client}>
      <FirstCommit seen={firstCommit}>
        <WaitState
          retryAt={retryAt}
          until={Date.parse(retryAt)}
          starting={false}
          onBack={vi.fn()}
          onStartAgain={vi.fn()}
        />
      </FirstCommit>
    </QueryClientProvider>,
  );
}

describe("SCR-02 wait state", () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date("2026-01-01T10:00:00") }));
  afterEach(() => vi.useRealTimers());

  it("counts down every second and offers Start again at zero", () => {
    renderWait("2026-01-01T10:01:05");
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

  it("inserts the live region empty and fills it after mount when the zone is already known (AC-02)", () => {
    const firstCommit: string[] = [];
    renderWait("2026-01-01T10:01:05", firstCommit);
    // a region inserted with its text already in it is not reliably spoken
    expect(firstCommit).toEqual([""]);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Telegram asks you to wait. You can try again at 10:01, in 1:05.",
    );
  });
});

describe("screens.md component fixes", () => {
  beforeEach(() => vi.useFakeTimers({ now: new Date("2026-01-01T10:00:00") }));
  afterEach(() => vi.useRealTimers());

  it("S11: the wait-state Back button is secondary, not ghost", () => {
    renderWait("2026-01-01T10:01:05");
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
