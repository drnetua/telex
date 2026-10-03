import { focusManager, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "./AppRoutes";
import { FailureBoundary } from "./FailureBoundary";
import { connectivity, resetConnectivity, setShellActive } from "../shell/connectivity";
import { PULSE_KEY } from "../shell/pulse";
import { createAppQueryClient } from "./queryClient";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const down = () => json(503, { code: "unavailable" });

function setup(
  entry: string | { pathname: string; state: unknown },
  client = createAppQueryClient(),
) {
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <FailureBoundary>
          <AppRoutes />
        </FailureBoundary>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const unavailable = () => screen.findByRole("heading", { level: 1, name: "teleX is unavailable" });

async function sendFromSignIn() {
  await userEvent.type(screen.getByLabelText("Email"), "me@example.com");
  await userEvent.click(screen.getByRole("button", { name: "Email me a sign-in link" }));
}

afterEach(() => {
  localStorage.clear();
  vi.unstubAllGlobals();
  resetConnectivity();
});

describe("failure routing inside the shell (AC-176)", () => {
  it("a 503 on an action keeps the screen and reports not-responding", async () => {
    setShellActive(true);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(down()));
    setup("/sign-in");
    await sendFromSignIn();
    await vi.waitFor(() => expect(connectivity.get()).toBe("not-responding"));
    expect(screen.queryByRole("heading", { name: "teleX is unavailable" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeVisible();
  });

  it("a 500 on an action still shows SCR-93", async () => {
    setShellActive(true);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(500, { code: "internal-error" })));
    setup("/sign-in");
    await sendFromSignIn();
    await unavailable();
    expect(connectivity.get()).toBe("online");
  });
});

describe("SCR-93 Retry carries the page action's outcome (AC-102, AC-103)", () => {
  it("503 on Send, then Retry, lands on Check your email", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(down())
      .mockResolvedValueOnce(json(201, { grantId: "g1", email: "me@example.com" }));
    vi.stubGlobal("fetch", fetchMock);
    setup("/sign-in");
    await sendFromSignIn();
    await unavailable();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "Check your email" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "teleX is unavailable" })).not.toBeInTheDocument();
  });

  it("503 on Send a new link, then Retry, makes the page use the new grant", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(down())
      .mockResolvedValueOnce(json(201, { grantId: "g2", email: "me@example.com" }))
      .mockResolvedValueOnce(json(200, { createdAccount: false }));
    vi.stubGlobal("fetch", fetchMock);
    setup({ pathname: "/sign-in/check-email", state: { grantId: "g1", email: "me@example.com" } });
    await userEvent.click(await screen.findByRole("button", { name: "Send a new link" }));
    await unavailable();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await screen.findByText(/We sent a new email/);
    await userEvent.click(screen.getAllByRole("textbox")[0] as HTMLElement);
    await userEvent.paste("482019");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await vi.waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(3));
    expect((fetchMock.mock.calls[2] as [string])[0]).toBe("/api/v1/sign-in/grants/g2/code");
  });

  it("a repeat failure says 'Still no answer.' and keeps SCR-93", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(down()));
    setup("/sign-in");
    await sendFromSignIn();
    await unavailable();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Still no answer.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "teleX is unavailable" })).toBeVisible();
  });

  it("renders SCR-93 inside the bare system layout (wordmark and card)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(down()));
    setup("/sign-in");
    await sendFromSignIn();
    const heading = await unavailable();
    expect(heading.closest(".card")).not.toBeNull();
    expect(heading.closest(".page-center")).not.toBeNull();
  });
});

describe("SCR-93 keeps the saved Retry across background failures (AC-102)", () => {
  // Inside the shell a 503 is the Status Banner's business; SCR-93 comes from an answered failure.
  const broken = () => json(500, { code: "internal-error" });

  function routeFetch(state: { signOut: Response[]; me: () => Response }) {
    return vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/v1/sign-out" && init?.method === "POST") {
        return Promise.resolve(state.signOut.shift() ?? json(204, {}));
      }
      if (url === "/api/v1/me") return Promise.resolve(state.me());
      if (url === "/api/v1/passkeys" || url === "/api/v1/sessions") {
        return Promise.resolve(json(200, { items: [] }));
      }
      return Promise.resolve(json(404, { code: "not-found" }));
    });
  }

  const blink = async () => {
    await act(async () => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });
  };

  it("a failing focus refetch under SCR-93 does not replace the saved Retry", async () => {
    let meUp = true;
    const fetchMock = routeFetch({
      signOut: [broken(), new Response(null, { status: 204 })],
      me: () => (meUp ? json(200, { ownerId: "o1", email: "me@example.com" }) : broken()),
    });
    vi.stubGlobal("fetch", fetchMock);
    setup("/profile");
    await userEvent.click(await screen.findByRole("button", { name: "Sign out" }));
    await unavailable();
    meUp = false;
    await blink();
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.filter(([u]) => u === "/api/v1/me").length).toBeGreaterThan(1),
    );
    // teleX is back before the Owner retries; a /me still failing afterwards would be a new failure.
    meUp = true;
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.filter(([u, i]) => u === "/api/v1/sign-out" && i?.method === "POST"),
      ).toHaveLength(2),
    );
    await vi.waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "teleX is unavailable" }),
      ).not.toBeInTheDocument(),
    );
  });

  it("a background failure during a successful Retry does not say 'Still no answer.'", async () => {
    let meUp = true;
    let finish: (r: Response) => void = () => undefined;
    const slow = new Promise<Response>((resolve) => (finish = resolve));
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === "/api/v1/sign-out" && init?.method === "POST") {
        return fetchMock.mock.calls.filter(([u]) => u === "/api/v1/sign-out").length === 1
          ? Promise.resolve(broken())
          : slow;
      }
      if (url === "/api/v1/me") {
        return Promise.resolve(meUp ? json(200, { ownerId: "o1", email: "m@e.com" }) : broken());
      }
      return Promise.resolve(json(200, { items: [] }));
    });
    vi.stubGlobal("fetch", fetchMock);
    setup("/profile");
    await userEvent.click(await screen.findByRole("button", { name: "Sign out" }));
    await unavailable();
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    meUp = false;
    await blink();
    await act(async () => finish(new Response(null, { status: 204 })));
    expect(screen.queryByText("Still no answer.")).not.toBeInTheDocument();
  });
});

describe("auth failures leave the shell cleanly (AC-173, AC-175)", () => {
  const refuse = (code: string) => vi.fn().mockResolvedValue(json(401, { code }));

  it("session-ended remembers the section before SCR-92 (AC-173)", async () => {
    vi.stubGlobal("fetch", refuse("session-ended"));
    setup("/profile");
    await screen.findByRole("heading", { level: 1, name: /session/i });
    expect(localStorage.getItem("telex.destination")).toBe("/profile");
  });

  it("session-ended keeps the first refusal (AC-173)", async () => {
    localStorage.setItem("telex.destination", "/runs");
    vi.stubGlobal("fetch", refuse("session-ended"));
    setup("/profile");
    await screen.findByRole("heading", { level: 1, name: /session/i });
    expect(localStorage.getItem("telex.destination")).toBe("/runs");
  });

  it("a sign-in failure clears the query cache (AC-175)", async () => {
    const client = createAppQueryClient();
    client.setQueryData(PULSE_KEY, { inboxCount: 7, conditions: [] });
    client.setQueryData(["me"], { ownerId: "o1", email: "old@example.com" });
    vi.stubGlobal("fetch", refuse("unauthenticated"));
    setup("/profile", client);
    await screen.findByLabelText("Email");
    expect(client.getQueryData(PULSE_KEY)).toBeUndefined();
    expect(client.getQueryData(["me"])).toBeUndefined();
  });
});
