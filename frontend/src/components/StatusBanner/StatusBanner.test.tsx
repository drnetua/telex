import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccountDisconnectedBanner } from "./AccountDisconnectedBanner";
import { StatusBanner } from "./StatusBanner";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const account = (id: string, name: string, state: string) => ({
  id,
  displayName: name,
  phone: { countryCode: "380", lastDigits: "42" },
  state,
  chatSync: { chatsSynced: 1, chatsTotal: 1, completedAt: "x" },
  linkedAt: "x",
});

afterEach(() => vi.unstubAllGlobals());

describe("StatusBanner", () => {
  it("renders icon, words and one action with no close button", async () => {
    const onClick = vi.fn();
    render(
      <StatusBanner
        icon="alert-circle"
        message="Something is wrong"
        action="Fix it"
        onAction={onClick}
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent("Something is wrong");
    expect(screen.queryByRole("button", { name: /close|dismiss/i })).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Fix it" }));
    expect(onClick).toHaveBeenCalled();
  });

  it("shows +N more", () => {
    render(
      <StatusBanner icon="alert-circle" message="m" action="a" onAction={() => {}} more={2} />,
    );
    expect(screen.getByText("+2 more")).toBeInTheDocument();
  });
});

function setup(accounts: unknown[], attempt?: (body: unknown) => void) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("linking-attempt") && init?.method === "POST") {
        attempt?.(JSON.parse(String(init.body)));
        return Promise.resolve(json(201, { step: "phone", origin: "accounts" }));
      }
      return Promise.resolve(json(200, { items: accounts }));
    }),
  );
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={["/x"]}>
        <AccountDisconnectedBanner />
        <Routes>
          <Route path="/x" element={<p>home</p>} />
          <Route path="/connect-telegram" element={<h1>Wizard</h1>} />
          <Route path="/accounts" element={<h1>Accounts page</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("AccountDisconnectedBanner", () => {
  it("AC-122: one lost account names it and signs in again", async () => {
    const attempt = vi.fn();
    setup([account("a1", "Anna", "session_lost"), account("a2", "Bob", "connected")], attempt);
    expect(await screen.findByText(/Anna's Telegram is disconnected/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign in again" }));
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "Wizard" })).toBeInTheDocument(),
    );
    expect(attempt).toHaveBeenCalledWith({ origin: "accounts", targetLinkedAccountId: "a1" });
  });

  it("several lost accounts open Accounts", async () => {
    setup([account("a1", "Anna", "session_lost"), account("a2", "Bob", "session_lost")]);
    expect(await screen.findByText("2 Telegram accounts are disconnected.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Open Accounts" }));
    expect(await screen.findByRole("heading", { name: "Accounts page" })).toBeInTheDocument();
  });

  it("shows nothing when no account is lost, including reconnecting", async () => {
    setup([account("a1", "Anna", "reconnecting")]);
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("AC-122: a refused Sign in again shows the refusal Toast and refetches the list", async () => {
    const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("linking-attempt") && init?.method === "POST")
        return Promise.resolve(json(409, { code: "linked-account-limit-reached", limit: 3 }));
      return Promise.resolve(json(200, { items: [account("a1", "Anna", "session_lost")] }));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={["/x"]}>
          <AccountDisconnectedBanner />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(
      await screen.findByText(
        "You've linked 3 accounts, the most this installation allows. Unlink an account to add another.",
      ),
    ).toBeInTheDocument();
    const listCalls = () =>
      fetchMock.mock.calls.filter((c) => c[0] === "/api/v1/linked-accounts").length;
    await waitFor(() => expect(listCalls()).toBeGreaterThan(1));
  });
});
