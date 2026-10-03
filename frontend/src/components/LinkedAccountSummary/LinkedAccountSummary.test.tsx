import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import type { LinkedAccount } from "../../api/linkedAccounts";
import { LinkedAccountSummary } from "./LinkedAccountSummary";

const base: LinkedAccount = {
  id: "a1",
  displayName: "Anna Petrenko",
  phone: { countryCode: "380", lastDigits: "42" },
  state: "connected",
  chatSync: { chatsSynced: 12, chatsTotal: 40, completedAt: null },
  linkedAt: "2026-01-01T00:00:00Z",
};

function show(account: LinkedAccount, variant: "line" | "row" = "row", actions?: ReactNode) {
  return render(
    <MemoryRouter>
      <LinkedAccountSummary account={account} variant={variant} actions={actions} />
    </MemoryRouter>,
  );
}

describe("LinkedAccountSummary", () => {
  it("AC-01: shows name, masked phone and syncing progress", () => {
    show(base);
    expect(screen.getByText("Anna Petrenko")).toBeInTheDocument();
    expect(screen.getByText("+380 ••• ••42")).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByText("Syncing chats: 12 of 40")).toBeInTheDocument();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "12");
    expect(bar).toHaveAttribute("aria-valuemax", "40");
  });

  it("shows an indeterminate progress while the total is unknown", () => {
    show({ ...base, chatSync: { chatsSynced: 0, chatsTotal: null, completedAt: null } });
    expect(screen.getByText("Syncing chats")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).not.toHaveAttribute("aria-valuenow");
  });

  it("shows the chat count once synced", () => {
    show({ ...base, chatSync: { chatsSynced: 40, chatsTotal: 40, completedAt: "2026-01-01" } });
    expect(screen.getByText("40 chats")).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).toBeNull();
  });

  it("AC-122: reconnecting and session lost show icon and words", () => {
    const { unmount } = show({ ...base, state: "reconnecting" });
    expect(screen.getByText("Reconnecting")).toBeInTheDocument();
    // the sync line keeps its last values while reconnecting
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    unmount();
    show({ ...base, state: "session_lost" });
    expect(screen.getByText("Session lost")).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).toBeNull();
  });

  it("announces the badge politely and renders the actions slot on rows", () => {
    show(base, "row", <button>Unlink</button>);
    expect(screen.getByText("Connected").closest("[aria-live]")).toHaveAttribute(
      "aria-live",
      "polite",
    );
    expect(screen.getByRole("button", { name: "Unlink" })).toBeInTheDocument();
  });

  it("line variant links the whole row to Accounts and has no actions", () => {
    show(base, "line", <button>Unlink</button>);
    expect(screen.getByRole("link")).toHaveAttribute("href", "/accounts");
    expect(screen.queryByRole("button")).toBeNull();
  });
});
