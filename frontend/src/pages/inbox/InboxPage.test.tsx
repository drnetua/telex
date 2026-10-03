import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Fragment } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FailureBoundary } from "../../app/FailureBoundary";
import { createAppQueryClient, failureBus } from "../../app/queryClient";
import { AppShell } from "../../shell/AppShell/AppShell";
import { InboxPage } from "./InboxPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const account = (id: string, displayName: string) => ({
  id,
  displayName,
  phone: { countryCode: "380", lastDigits: "42" },
  state: "connected",
  chatSync: { chatsSynced: 3, chatsTotal: 3, completedAt: "2026-01-01T00:00:00Z" },
  linkedAt: "2026-01-01T00:00:00Z",
});
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };

function LocationState() {
  return <span data-testid="loc-state">{JSON.stringify(useLocation().state)}</span>;
}

function setup(bounded = true, state: unknown = null) {
  const Shell = bounded ? FailureBoundary : Fragment;
  const client = createAppQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[{ pathname: "/inbox", state }]}>
        <Shell>
          <Routes>
            <Route
              path="/inbox"
              element={
                <AppShell email="me@example.com">
                  <InboxPage />
                  <LocationState />
                </AppShell>
              }
            />
            <Route path="/connect-telegram" element={<h1>Wizard page</h1>} />
            <Route path="/sign-in" element={<h1>Sign in page</h1>} />
          </Routes>
        </Shell>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

afterEach(() => vi.unstubAllGlobals());

/** Answers by URL: the shell also reads the pulse and linked accounts for its banners. */
function routed(handlers: {
  signOut: () => Response;
  start?: () => Response;
  accounts?: unknown[];
}) {
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url === "/api/v1/sign-out") return Promise.resolve(handlers.signOut());
    if (url === "/api/v1/pulse") return Promise.resolve(json(200, { inboxCount: 0, conditions: [] }));
    if (url === "/api/v1/linked-accounts")
      return Promise.resolve(json(200, { items: handlers.accounts ?? [] }));
    if (url === "/api/v1/linking-attempt")
      return Promise.resolve(
        handlers.start?.() ??
          json(201, { step: "phone", origin: "inbox", targetLinkedAccountId: null }),
      );
    return Promise.resolve(json(200, me));
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("SCR-10 Inbox", () => {
  it("AC-100: empty Inbox offers the single step Connect Telegram", async () => {
    routed({ signOut: () => new Response(null, { status: 204 }) });
    setup();
    expect(await screen.findByRole("heading", { level: 1, name: "Inbox" })).toBeInTheDocument();
    expect(await screen.findByText("Connect your Telegram account to start.")).toBeInTheDocument();
    // C5: screens.md SCR-10 has the h1 and the sentence only, no extra heading.
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Connect Telegram" })).toBeEnabled();
  });

  it("AC-01: Connect Telegram starts linking from the inbox and opens the wizard", async () => {
    const fetchMock = routed({ signOut: () => new Response(null, { status: 204 }) });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Connect Telegram" }));
    expect(await screen.findByRole("heading", { name: "Wizard page" })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find((c) => c[0] === "/api/v1/linking-attempt") as [
      string,
      RequestInit,
    ];
    expect(call[1].method).toBe("POST");
    expect(JSON.parse(call[1].body as string)).toEqual({ origin: "inbox" });
    expect(screen.queryByText("Telegram linking is coming next.")).not.toBeInTheDocument();
  });

  it("AC-119: a refused start shows the not-set-up toast and does not open the wizard", async () => {
    routed({
      signOut: () => new Response(null, { status: 204 }),
      start: () => json(503, { code: "telegram-linking-not-set-up" }),
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Connect Telegram" }));
    expect(
      await screen.findByText(
        "Telegram linking isn't set up on this installation yet. The person who runs teleX has to finish the setup.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Wizard page" })).not.toBeInTheDocument();
    // The refusal stays on the page; the app-wide unavailable screen (SCR-93) must not cover it.
    expect(screen.queryByRole("heading", { name: "teleX is unavailable" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Telegram" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Connect Telegram" })).toBeEnabled();
  });

  it("AC-01: with accounts the Inbox lists one line per account and no Connect step", async () => {
    routed({
      signOut: () => new Response(null, { status: 204 }),
      accounts: [account("a1", "Ann"), account("a2", "Bob")],
    });
    setup();
    expect(await screen.findByText("Ann")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Ann|Bob/ })).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: /Ann/ })[0]).toHaveAttribute("href", "/accounts");
    expect(screen.queryByRole("button", { name: "Connect Telegram" })).not.toBeInTheDocument();
    expect(screen.getAllByText("+380 ••• ••42")).toHaveLength(2);
  });

  it("shows loading while linked accounts load", async () => {
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(() => {})));
    setup();
    expect(await screen.findByRole("status")).toHaveAttribute("aria-busy", "true");
  });

  it("AC-95: Sign out posts, clears cached Owner data and lands on sign-in", async () => {
    const fetchMock = routed({ signOut: () => new Response(null, { status: 204 }) });
    vi.stubGlobal("fetch", fetchMock);
    const client = setup();
    await screen.findByRole("heading", { level: 1, name: "Inbox" });
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls.find((c) => c[0] === "/api/v1/sign-out") as [
      string,
      RequestInit,
    ];
    expect(url).toBe("/api/v1/sign-out");
    expect(init.method).toBe("POST");
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it("sign-out failure routes to unavailable and keeps the Inbox", async () => {
    const handler = vi.fn();
    failureBus.handler = handler;
    vi.stubGlobal("fetch", routed({ signOut: () => json(403, { code: "forbidden" }) }));
    setup(false);
    await screen.findByRole("heading", { level: 1, name: "Inbox" });
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(handler).toHaveBeenCalled());
    expect(screen.getByRole("heading", { level: 1, name: "Inbox" })).toBeInTheDocument();
  });

  it("AC-114: the arrival Toast is cleared from history so reload and Back do not repeat it", async () => {
    routed({
      signOut: () => new Response(null, { status: 204 }),
      accounts: [account("a1", "Ann")],
    });
    setup(true, { toast: "Ann is connected" });
    expect(await screen.findByText("Ann is connected")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("loc-state")).toHaveTextContent("null"));
  });
});
