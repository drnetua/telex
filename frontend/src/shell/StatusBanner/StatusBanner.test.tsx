import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { connectivity, resetConnectivity, retryNow } from "../connectivity";
import { StatusBanner } from "./StatusBanner";

vi.mock("../connectivity", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../connectivity")>()),
  retryNow: vi.fn(),
}));

afterEach(() => {
  vi.restoreAllMocks();
  resetConnectivity();
});

function show(conditions: string[] = []) {
  return render(
    <MemoryRouter>
      <StatusBanner conditions={conditions} />
    </MemoryRouter>,
  );
}

describe("StatusBanner", () => {
  it("renders nothing while all is well", () => {
    show();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("shows offline with words, an icon, Try again and no close control", () => {
    act(() => window.dispatchEvent(new Event("offline")));
    show();
    const banner = screen.getByRole("status");
    expect(banner).toHaveTextContent(
      "You're offline. teleX will update when your connection is back.",
    );
    expect(banner.querySelector("svg")).not.toBeNull();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /close|dismiss/i })).toBeNull();
  });

  it("shows not-responding", () => {
    connectivity.reportNoAnswer();
    show();
    expect(screen.getByRole("status")).toHaveTextContent("teleX isn't responding.");
  });

  it("shows busy on Try again, then the still-down text when it stays down", async () => {
    connectivity.reportNoAnswer();
    let finish: (v: { stillDown: boolean }) => void = () => undefined;
    vi.mocked(retryNow).mockReturnValue(
      new Promise((r) => {
        finish = r;
      }),
    );
    show();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(screen.getByRole("button", { name: "Trying again" })).toBeDisabled();
    await act(async () => finish({ stillDown: true }));
    expect(screen.getByRole("status")).toHaveTextContent(
      "Still can't reach teleX. It keeps trying on its own.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  it("disappears on recovery", () => {
    connectivity.reportNoAnswer();
    show();
    expect(screen.getByRole("status")).toBeInTheDocument();
    act(() => connectivity.reportAnswered());
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("shows the most important condition and lists the others under +N more with own actions", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    connectivity.reportNoAnswer();
    show(["account-disconnected", "foo-bar"]);
    expect(screen.getByRole("status")).toHaveTextContent("teleX isn't responding.");
    const more = screen.getByRole("button", { name: "+1 more" });
    expect(more).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText(/disconnected from Telegram/)).toBeNull();
    await userEvent.click(more);
    expect(more).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText(/disconnected from Telegram/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reconnect" })).toBeInTheDocument();
  });

  it("shows a server condition alone with its own action", () => {
    show(["budget-exhausted"]);
    expect(screen.getByRole("status")).toHaveTextContent(/budget/i);
    expect(screen.queryByRole("button", { name: /more/ })).toBeNull();
  });

  it("keeps an unknown code out of the banner", () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    show(["foo-bar"]);
    expect(screen.queryByRole("status")).toBeNull();
  });
});
