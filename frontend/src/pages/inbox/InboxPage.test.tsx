import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppQueryClient, failureBus } from "../../app/queryClient";
import { AppShell } from "../../shell/AppShell/AppShell";
import { InboxPage } from "./InboxPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };

function setup() {
  const client = createAppQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/inbox"]}>
        <Routes>
          <Route
            path="/inbox"
            element={
              <AppShell email="me@example.com">
                <InboxPage />
              </AppShell>
            }
          />
          <Route path="/sign-in" element={<h1>Sign in page</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

afterEach(() => vi.unstubAllGlobals());

describe("SCR-10 Inbox", () => {
  it("AC-100: empty Inbox offers the single step Connect Telegram", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, me)));
    setup();
    expect(await screen.findByRole("heading", { level: 1, name: "Inbox" })).toBeInTheDocument();
    expect(screen.getByText("Connect your Telegram account to start.")).toBeInTheDocument();
    // C5: screens.md SCR-10 has the h1 and the sentence only, no extra heading.
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Connect Telegram" })).toBeEnabled();
  });

  it("Connect Telegram shows the info toast, and shows it afresh on a second choice", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, me)));
    setup();
    const connect = await screen.findByRole("button", { name: "Connect Telegram" });
    await userEvent.click(connect);
    const first = await screen.findByText("Telegram linking is coming next.");
    await userEvent.click(connect);
    // The toast is dropped and shown again, so the second choice gets a full display time.
    await waitFor(() => {
      const second = screen.getByText("Telegram linking is coming next.");
      expect(second).not.toBe(first);
      expect(first.isConnected).toBe(false);
    });
  });

  it("AC-95: Sign out posts, clears cached Owner data and lands on sign-in", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, me))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = setup();
    await screen.findByRole("heading", { level: 1, name: "Inbox" });
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-out");
    expect(init.method).toBe("POST");
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it("sign-out failure routes to unavailable and keeps the Inbox", async () => {
    const handler = vi.fn();
    failureBus.handler = handler;
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, me))
        .mockResolvedValueOnce(json(403, { code: "forbidden" })),
    );
    setup();
    await screen.findByRole("heading", { level: 1, name: "Inbox" });
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(handler).toHaveBeenCalled());
    expect(screen.getByRole("heading", { level: 1, name: "Inbox" })).toBeInTheDocument();
  });
});
