import { onlineManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { connectivity, resetConnectivity, retryNow } from "../connectivity";
import { StatusBanner } from "./StatusBanner";

vi.mock("../connectivity", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../connectivity")>()),
  retryNow: vi.fn(),
}));

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  resetConnectivity();
  // A down connection also marks the query client offline; the next test starts online.
  onlineManager.setOnline(true);
});

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

/** Stubs the Linked Accounts list and the linking-attempt start; returns the start bodies seen. */
function stubAccounts(accounts: unknown[], start?: () => Response) {
  const started: unknown[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      if (url.includes("linking-attempt") && init?.method === "POST") {
        started.push(JSON.parse(String(init.body)));
        return Promise.resolve(
          start?.() ??
            json(201, { step: "phone", origin: "accounts", targetLinkedAccountId: "a1" }),
        );
      }
      return Promise.resolve(json(200, { items: accounts }));
    }),
  );
  return started;
}

// "always": a down connection pauses fetches, and these tests drive the banner with a stubbed fetch.
const client = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false, networkMode: "always" } } });

function show(conditions: string[] = [], queryClient: QueryClient = client()) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/x"]}>
        <StatusBanner conditions={conditions} />
        <Routes>
          <Route path="/x" element={<p>home</p>} />
          <Route path="/connect-telegram" element={<h1>Wizard</h1>} />
          <Route path="/accounts" element={<h1>Accounts page</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("StatusBanner", () => {
  it("AC-176: keeps an empty live region while all is well", () => {
    show();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("AC-176: the live region element survives from empty to the first condition", () => {
    const view = show();
    const region = screen.getByRole("status");
    view.rerender(
      <QueryClientProvider client={client()}>
        <MemoryRouter>
          <StatusBanner conditions={["bot-blocked"]} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region).not.toBeEmptyDOMElement();
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
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("shows the most important condition and lists the others under +N more with own actions", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    stubAccounts([account("a1", "Anna", "session_lost")]);
    connectivity.reportNoAnswer();
    show(["account-disconnected", "foo-bar"]);
    expect(screen.getByRole("status")).toHaveTextContent("teleX isn't responding.");
    const more = screen.getByRole("button", { name: "+1 more" });
    expect(more).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText(/is disconnected/)).toBeNull();
    await userEvent.click(more);
    expect(more).toHaveAttribute("aria-expanded", "true");
    expect(await screen.findByText(/Anna's Telegram is disconnected/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in again" })).toBeInTheDocument();
  });

  it("AC-122: offline plus Session lost is one banner with +1 more, not two", () => {
    stubAccounts([account("a1", "Anna", "session_lost")]);
    act(() => window.dispatchEvent(new Event("offline")));
    show(["account-disconnected"]);
    expect(screen.getAllByRole("status")).toHaveLength(1);
    expect(screen.getByRole("status")).toHaveTextContent("You're offline.");
    expect(screen.getByRole("button", { name: "+1 more" })).toBeInTheDocument();
  });

  it("AC-122: account-disconnected names the one Session lost account and signs in again against it", async () => {
    const started = stubAccounts([
      account("a1", "Anna", "session_lost"),
      account("a2", "Bob", "connected"),
    ]);
    show(["account-disconnected"]);
    expect(await screen.findByText(/Anna's Telegram is disconnected/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Sign in again" }));
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "Wizard" })).toBeInTheDocument(),
    );
    expect(started).toEqual([{ origin: "accounts", targetLinkedAccountId: "a1" }]);
  });

  it("AC-117: Sign in again puts the started attempt in the linking-attempt cache", async () => {
    stubAccounts([account("a1", "Anna", "session_lost")]);
    const c = client();
    render(
      <QueryClientProvider client={c}>
        <MemoryRouter>
          <StatusBanner conditions={["account-disconnected"]} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    await waitFor(() =>
      expect(c.getQueryData(["linking-attempt"])).toMatchObject({ targetLinkedAccountId: "a1" }),
    );
  });

  it("AC-122: several Session lost accounts use the plural copy and open Accounts", async () => {
    stubAccounts([account("a1", "Anna", "session_lost"), account("a2", "Bob", "session_lost")]);
    show(["account-disconnected"]);
    expect(await screen.findByText("2 Telegram accounts are disconnected.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Open Accounts" }));
    expect(await screen.findByRole("heading", { name: "Accounts page" })).toBeInTheDocument();
  });

  it("AC-122: a refused Sign in again toasts the refusal and refetches the list", async () => {
    const started = stubAccounts([account("a1", "Anna", "session_lost")], () =>
      json(409, { code: "linked-account-limit-reached", limit: 3 }),
    );
    show(["account-disconnected"]);
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    expect(await screen.findByText(/You've linked 3 accounts/)).toBeInTheDocument();
    expect(started).toHaveLength(1);
    const calls = vi.mocked(fetch).mock.calls;
    await waitFor(() =>
      expect(calls.filter((c) => c[0] === "/api/v1/linked-accounts").length).toBeGreaterThan(1),
    );
  });

  it("AC-122: a loaded list without a Session lost account hides a condition the pulse still reports", async () => {
    const started = stubAccounts([account("a1", "Anna", "connected")]);
    show(["account-disconnected"]);
    await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByRole("status")).toBeEmptyDOMElement());
    expect(screen.queryByText(/disconnected from Telegram/)).toBeNull();
    expect(started).toHaveLength(0);
  });

  it("AC-117: Sign in again still navigates and caches the attempt when connectivity outranks the line mid-request", async () => {
    let finish: (r: Response) => void = () => undefined;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string, init?: RequestInit) =>
        url.includes("linking-attempt") && init?.method === "POST"
          ? new Promise<Response>((r) => {
              finish = r;
            })
          : Promise.resolve(json(200, { items: [account("a1", "Anna", "session_lost")] })),
      ),
    );
    const c = client();
    render(
      <QueryClientProvider client={c}>
        <MemoryRouter initialEntries={["/x"]}>
          <StatusBanner conditions={["account-disconnected"]} />
          <Routes>
            <Route path="/x" element={<p>home</p>} />
            <Route path="/connect-telegram" element={<h1>Wizard</h1>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    act(() => connectivity.reportNoAnswer());
    expect(screen.getByRole("status")).toHaveTextContent("teleX isn't responding.");
    await act(async () =>
      finish(json(201, { step: "phone", origin: "accounts", targetLinkedAccountId: "a1" })),
    );
    expect(await screen.findByRole("heading", { name: "Wizard" })).toBeInTheDocument();
    expect(c.getQueryData(["linking-attempt"])).toMatchObject({ targetLinkedAccountId: "a1" });
  });

  it("AC-122, AC-117: a refusal Toast keeps its node when the refetch that follows clears the lost account", async () => {
    let lists = 0;
    // The refetch answers only after the Toast is up, so the banner line disappears underneath it.
    let release: () => void = () => undefined;
    const refetchAnswered = new Promise<void>((resolve) => (release = resolve));
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string, init?: RequestInit) => {
        if (url.includes("linking-attempt") && init?.method === "POST")
          return Promise.resolve(json(409, { code: "linked-account-limit-reached", limit: 3 }));
        lists += 1;
        if (lists === 1)
          return Promise.resolve(json(200, { items: [account("a1", "Anna", "session_lost")] }));
        return refetchAnswered.then(() =>
          json(200, { items: [account("a1", "Anna", "connected")] }),
        );
      }),
    );
    show(["account-disconnected"]);
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    const toast = await screen.findByText(/You've linked 3 accounts/);
    expect(screen.getByRole("button", { name: "Sign in again" })).toBeInTheDocument();
    await act(async () => release());
    await waitFor(() => expect(screen.getByRole("status")).toBeEmptyDOMElement());
    // The same node: a remount would announce the refusal a second time.
    expect(screen.getByText(/You've linked 3 accounts/)).toBe(toast);
  });

  it("shows a server condition alone with its own action", () => {
    show(["budget-exhausted"]);
    expect(screen.getByRole("status")).toHaveTextContent(/budget/i);
    expect(screen.queryByRole("button", { name: /more/ })).toBeNull();
  });

  it("keeps an unknown code out of the banner", () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    show(["foo-bar"]);
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("AC-176: does not fetch the Linked Accounts list while the pulse reports no account-disconnected", async () => {
    stubAccounts([account("a1", "Anna", "session_lost")]);
    show(["offline-ish-unknown"]);
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
    });
    expect(vi.mocked(fetch)).not.toHaveBeenCalled();
  });

  it("AC-122: a stale all-connected list is refetched when the pulse starts reporting the condition", async () => {
    stubAccounts([account("a1", "Anna", "session_lost")]);
    const c = client();
    c.setQueryData(["linked-accounts"], [account("a1", "Anna", "connected")]);
    const view = show([], c);
    view.rerender(
      <QueryClientProvider client={c}>
        <MemoryRouter initialEntries={["/x"]}>
          <StatusBanner conditions={["account-disconnected"]} />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText(/Anna's Telegram is disconnected/)).toBeInTheDocument();
  });

  it("AC-117: a started Sign in again invalidates the pulse", async () => {
    stubAccounts([account("a1", "Anna", "session_lost")]);
    const c = client();
    const spy = vi.spyOn(c, "invalidateQueries");
    show(["account-disconnected"], c);
    await userEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
    await waitFor(() => expect(spy).toHaveBeenCalledWith({ queryKey: ["pulse"] }));
  });
});
