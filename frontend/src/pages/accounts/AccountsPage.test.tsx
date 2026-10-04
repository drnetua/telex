import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FailureBoundary } from "../../app/FailureBoundary";
import { createAppQueryClient } from "../../app/queryClient";
import { AccountsPage } from "./AccountsPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const account = (id: string, displayName: string, state = "connected") => ({
  id,
  displayName,
  phone: { countryCode: "380", lastDigits: "42" },
  state,
  chatSync: { chatsSynced: 12, chatsTotal: 12, completedAt: "2026-01-01T00:00:00Z" },
  linkedAt: "2026-01-01T00:00:00Z",
});

interface Handlers {
  accounts: () => unknown[];
  start?: () => Response;
  unlink?: () => Response;
  state?: unknown;
}

function LocationState() {
  return <span data-testid="loc-state">{JSON.stringify(useLocation().state)}</span>;
}

function setup(handlers: Handlers) {
  const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (url === "/api/v1/linked-accounts")
      return Promise.resolve(json(200, { items: handlers.accounts() }));
    if (url === "/api/v1/linking-attempt" && init?.method === "POST")
      return Promise.resolve(
        handlers.start?.() ??
          json(201, { step: "phone", origin: "accounts", targetLinkedAccountId: null }),
      );
    if (url.startsWith("/api/v1/linked-accounts/") && init?.method === "DELETE")
      return Promise.resolve(handlers.unlink?.() ?? json(200, { signOutConfirmed: true }));
    return Promise.resolve(json(404, { code: "not-found" }));
  });
  vi.stubGlobal("fetch", fetchMock);
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <MemoryRouter initialEntries={[{ pathname: "/accounts", state: handlers.state ?? null }]}>
        <FailureBoundary>
          <Routes>
            <Route
              path="/accounts"
              element={
                <>
                  <AccountsPage />
                  <LocationState />
                </>
              }
            />
            <Route path="/connect-telegram" element={<h1>Wizard page</h1>} />
            <Route path="/inbox" element={<h1>Inbox page</h1>} />
          </Routes>
        </FailureBoundary>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("SCR-60 Accounts", () => {
  it("AC-114: lists every account with name, masked phone and state, plus Add account", async () => {
    setup({ accounts: () => [account("a1", "Ann"), account("a2", "Bob", "reconnecting")] });
    expect(await screen.findByRole("heading", { level: 1, name: "Accounts" })).toBeInTheDocument();
    expect(
      screen.getByText("Telegram accounts teleX works with. Each one syncs its own chats."),
    ).toBeInTheDocument();
    expect(screen.getByText("Linked accounts")).toBeInTheDocument();
    expect(screen.getByText("Ann")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getAllByText("+380 ••• ••42")).toHaveLength(2);
    expect(screen.getByText("Connected")).toBeInTheDocument();
    expect(screen.getByText("Reconnecting")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Unlink" })).toHaveLength(2);
    expect(screen.queryByRole("button", { name: "Sign in again" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add account" })).toBeEnabled();
  });

  it("shows loading while accounts load", async () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    render(
      <QueryClientProvider client={createAppQueryClient()}>
        <MemoryRouter>
          <AccountsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByRole("status")).toHaveAttribute("aria-busy", "true");
  });

  it("empty: first-run state with one Add account and no header button", async () => {
    setup({ accounts: () => [] });
    expect(await screen.findByText("No Telegram accounts linked yet.")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add account" })).toHaveLength(1);
  });

  it("Add account starts linking from accounts and opens the wizard", async () => {
    const fetchMock = setup({ accounts: () => [account("a1", "Ann")] });
    await userEvent.click(await screen.findByRole("button", { name: "Add account" }));
    expect(await screen.findByRole("heading", { name: "Wizard page" })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(
      (c) => c[0] === "/api/v1/linking-attempt" && c[1]?.method === "POST",
    ) as [string, RequestInit];
    expect(JSON.parse(call[1].body as string)).toEqual({ origin: "accounts" });
  });

  it("session lost: Sign in again starts linking for that account", async () => {
    const fetchMock = setup({ accounts: () => [account("a1", "Ann", "session_lost")] });
    expect(await screen.findByText("Session lost")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign in again" }));
    expect(await screen.findByRole("heading", { name: "Wizard page" })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(
      (c) => c[0] === "/api/v1/linking-attempt" && c[1]?.method === "POST",
    ) as [string, RequestInit];
    expect(JSON.parse(call[1].body as string)).toEqual({
      origin: "accounts",
      targetLinkedAccountId: "a1",
    });
  });

  it("start refused by the limit shows the limit toast and stays", async () => {
    setup({
      accounts: () => [account("a1", "Ann")],
      start: () => json(409, { code: "linked-account-limit-reached", limit: 3 }),
    });
    await userEvent.click(await screen.findByRole("button", { name: "Add account" }));
    expect(
      await screen.findByText(
        "You've linked 3 accounts, the most this installation allows. Unlink an account to add another.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Wizard page" })).not.toBeInTheDocument();
  });

  it("start refused as not set up shows the not-set-up toast", async () => {
    setup({
      accounts: () => [account("a1", "Ann")],
      start: () => json(503, { code: "telegram-linking-not-set-up" }),
    });
    await userEvent.click(await screen.findByRole("button", { name: "Add account" }));
    expect(await screen.findByText(/Telegram linking isn't set up/)).toBeVisible();
    expect(screen.queryByRole("heading", { name: "teleX is unavailable" })).not.toBeInTheDocument();
  });

  it("start refused as telegram-unavailable shows its toast and does not open the wizard", async () => {
    setup({
      accounts: () => [account("a1", "Ann")],
      start: () => json(503, { code: "telegram-unavailable" }),
    });
    await userEvent.click(await screen.findByRole("button", { name: "Add account" }));
    expect(
      await screen.findByText("Telegram didn't answer. Check your connection and try again."),
    ).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Wizard page" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "teleX is unavailable" })).not.toBeInTheDocument();
  });

  it("start refused as already linked shows the toast and refetches", async () => {
    let lost = true;
    const fetchMock = setup({
      accounts: () => [account("a1", "Ann", lost ? "session_lost" : "connected")],
      start: () => {
        lost = false;
        return json(409, { code: "telegram-account-already-linked" });
      },
    });
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(await screen.findByText("This account is already connected.")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("button", { name: "Sign in again" })).toBeNull());
    expect(fetchMock.mock.calls.filter((c) => c[0] === "/api/v1/linked-accounts").length).toBe(2);
  });

  it("AC-03: Sign in again on a missing account refetches and the row disappears silently", async () => {
    let gone = false;
    setup({
      accounts: () =>
        gone
          ? [account("a2", "Bob")]
          : [account("a1", "Ann", "session_lost"), account("a2", "Bob")],
      start: () => {
        gone = true;
        return json(404, { code: "not-found" });
      },
    });
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    await waitFor(() => expect(screen.queryByText("Ann")).toBeNull());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
  });

  it("unlink confirm names the consequences and Keep account closes it", async () => {
    setup({ accounts: () => [account("a1", "Ann"), account("a2", "Bob")] });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    const dialog = screen.getByRole("dialog", { name: "Unlink Ann?" });
    expect(
      within(dialog).getByText(
        "teleX will sign out of this Telegram account and delete its session and the 12 chats it synced. To use it in teleX again, you'll link it from the start.",
      ),
    ).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Keep account" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("AC-111: confirmed unlink deletes, refetches and shows an info toast", async () => {
    let gone = false;
    const fetchMock = setup({
      accounts: () =>
        gone ? [account("a2", "Bob")] : [account("a1", "Ann"), account("a2", "Bob")],
      unlink: () => {
        gone = true;
        return json(200, { signOutConfirmed: true });
      },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    expect(await screen.findByText("Ann is unlinked.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("Ann", { selector: ".fw-medium" })).toBeNull());
    const call = fetchMock.mock.calls.find((c) => c[1]?.method === "DELETE") as [
      string,
      RequestInit,
    ];
    expect(call[0]).toBe("/api/v1/linked-accounts/a1");
  });

  it("AC-111: unlinking the last account lands on the Inbox", async () => {
    let gone = false;
    setup({
      accounts: () => (gone ? [] : [account("a1", "Ann")]),
      unlink: () => {
        gone = true;
        return json(200, { signOutConfirmed: true });
      },
    });
    await userEvent.click(await screen.findByRole("button", { name: "Unlink" }));
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    expect(await screen.findByRole("heading", { name: "Inbox page" })).toBeInTheDocument();
  });

  it("AC-113: unconfirmed sign-out keeps an error toast telling the Owner to check sessions", async () => {
    setup({
      accounts: () => [account("a1", "Ann", "session_lost"), account("a2", "Bob")],
      unlink: () => json(200, { signOutConfirmed: false }),
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    const toast = await screen.findByRole("alert");
    expect(toast).toHaveTextContent(
      "Ann is unlinked and teleX deleted everything it kept. Telegram couldn't confirm the sign-out, so check Active sessions in the Telegram app and end teleX there if it's listed.",
    );
  });

  it("AC-03: unlink of a missing account closes the dialog and refetches without a message", async () => {
    let gone = false;
    setup({
      accounts: () =>
        gone ? [account("a2", "Bob")] : [account("a1", "Ann"), account("a2", "Bob")],
      unlink: () => {
        gone = true;
        return json(404, { code: "not-found" });
      },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.queryByText("Ann")).toBeNull());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText("Ann is unlinked.")).not.toBeInTheDocument();
  });

  it("AC-114: shows the arrival Toast from the wizard and clears it from history", async () => {
    setup({
      accounts: () => [account("a1", "Ann")],
      state: { toast: "Ann is connected" },
    });
    expect(await screen.findByText("Ann is connected")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("loc-state")).toHaveTextContent("null"));
  });

  it("AC-117: shows the connected again Toast after Sign in again", async () => {
    setup({
      accounts: () => [account("a1", "Ann")],
      state: { toast: "Ann is connected again" },
    });
    expect(await screen.findByText("Ann is connected again")).toBeInTheDocument();
  });

  it("AC-122: a reconnecting row keeps its sync line and explains itself", async () => {
    setup({ accounts: () => [account("a1", "Ann", "reconnecting")] });
    expect(
      await screen.findByText("Telegram can't be reached right now. teleX reconnects by itself."),
    ).toBeInTheDocument();
    expect(screen.getByText("12 chats")).toBeInTheDocument();
  });

  it("AC-117: a session lost row explains how to recover", async () => {
    setup({ accounts: () => [account("a1", "Ann", "session_lost")] });
    expect(
      await screen.findByText(
        "The session was ended in Telegram. Sign in again to bring this account back with everything attached.",
      ),
    ).toBeInTheDocument();
  });

  it("AC-111: focus moves to the page heading after the unlinked row is gone", async () => {
    let accounts = [account("a1", "Ann"), account("a2", "Bob")];
    setup({
      accounts: () => accounts,
      unlink: () => {
        accounts = [accounts[1]!];
        return json(200, { signOutConfirmed: true });
      },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Unlink account" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.getByRole("heading", { level: 1, name: "Accounts" })).toHaveFocus(),
    );
  });

  it("AC-111: a cancelled dialog after an earlier unlink returns focus to that row's Unlink button", async () => {
    let accounts = [account("a1", "Ann"), account("a2", "Bob"), account("a3", "Cy")];
    setup({
      accounts: () => accounts,
      unlink: () => {
        accounts = accounts.slice(1);
        return json(200, { signOutConfirmed: true });
      },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: "Unlink account" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    await waitFor(() => expect(screen.queryByText("Ann")).toBeNull());
    const second = (await screen.findAllByRole("button", { name: "Unlink" }))[1]!;
    await userEvent.click(second);
    await userEvent.click(
      within(await screen.findByRole("dialog")).getByRole("button", { name: "Keep account" }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(screen.getAllByRole("button", { name: "Unlink" })[1]).toHaveFocus();
  });

  it("AC-111: unlinking an account that is already gone puts focus on the page heading", async () => {
    let gone = false;
    setup({
      accounts: () =>
        gone ? [account("a2", "Bob")] : [account("a1", "Ann"), account("a2", "Bob")],
      unlink: () => {
        gone = true;
        return json(404, { code: "not-found" });
      },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    await waitFor(() => expect(screen.queryByText("Ann")).toBeNull());
    expect(screen.getByRole("heading", { level: 1, name: "Accounts" })).toHaveFocus();
  });

  it("AC-114: Toasts open together are both readable in one container", async () => {
    setup({
      accounts: () => [account("a1", "Ann"), account("a2", "Bob")],
      unlink: () => json(200, { signOutConfirmed: false }),
      state: { toast: "Bob is connected" },
    });
    await userEvent.click((await screen.findAllByRole("button", { name: "Unlink" }))[0]!);
    await userEvent.click(screen.getByRole("button", { name: "Unlink account" }));
    expect(await screen.findByText(/Telegram couldn't confirm the sign-out/)).toBeVisible();
    expect(screen.getByText("Bob is connected")).toBeVisible();
    expect(document.querySelectorAll(".telex-toast-container")).toHaveLength(1);
  });
});
