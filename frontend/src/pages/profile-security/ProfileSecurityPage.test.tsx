import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppQueryClient } from "../../app/queryClient";
import { PageFrame } from "../../components/PageFrame/PageFrame";
import { ProfileSecurityPage } from "./ProfileSecurityPage";

vi.mock("../../api/webauthn", async (orig) => ({
  ...(await orig<object>()),
  canCreatePasskey: vi.fn().mockResolvedValue(true),
  createPasskey: vi.fn().mockResolvedValue(undefined),
}));

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const me = { ownerId: "o1", email: "me@example.com", linkedAccountCount: 0 };
const passkey = {
  id: "p1",
  label: "Safari on iPhone",
  createdAt: "2026-10-02T08:00:00Z",
  lastUsedAt: null,
};
const sessions = {
  items: [
    {
      id: "s1",
      userAgentLabel: "Chrome on Mac",
      deviceType: "computer",
      startedAt: "2026-10-01T08:00:00Z",
      lastActivityAt: new Date().toISOString(),
      current: true,
    },
    {
      id: "s2",
      userAgentLabel: "Safari on iPhone",
      deviceType: "phone",
      startedAt: "2026-10-01T08:00:00Z",
      lastActivityAt: new Date().toISOString(),
      current: false,
    },
  ],
};

/** Routes fetches by method + url; later passkey/session lists can be replaced per call. */
function stubApi(opts: {
  passkeys?: unknown[];
  sessionList?: unknown;
  onCall?: (method: string, url: string) => Response | undefined;
}) {
  const calls: Array<[string, string]> = [];
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    calls.push([method, url]);
    const custom = opts.onCall?.(method, url);
    if (custom) return Promise.resolve(custom);
    if (url === "/api/v1/me") return Promise.resolve(json(200, me));
    if (url === "/api/v1/passkeys" && method === "GET")
      return Promise.resolve(json(200, { items: opts.passkeys ?? [passkey] }));
    if (url === "/api/v1/sessions" && method === "GET")
      return Promise.resolve(json(200, opts.sessionList ?? sessions));
    return Promise.resolve(new Response(null, { status: 204 }));
  });
  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

function setup() {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
      <MemoryRouter initialEntries={["/profile"]}>
        <Routes>
          <Route
            path="/profile"
            element={
              <PageFrame>
                <ProfileSecurityPage />
              </PageFrame>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("SCR-64 Profile and security", () => {
  it("shows heading and signed-in email", async () => {
    stubApi({});
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "Profile and security" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Signed in as me@example.com")).toBeInTheDocument();
  });

  it("AC-89: lists passkey with label and Never used", async () => {
    stubApi({});
    setup();
    expect(
      await screen.findByRole("heading", { name: "Safari on iPhone", level: 4 }),
    ).toBeVisible();
    expect(screen.getByText(/Never used/)).toBeInTheDocument();
    expect(screen.getByText(/Created/)).toBeInTheDocument();
  });

  it("AC-91: no passkeys shows empty state with Add a passkey", async () => {
    stubApi({ passkeys: [] });
    setup();
    expect(await screen.findByText("No passkeys yet.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add a passkey" })).toBeEnabled();
  });

  it("AC-92: remove asks for confirmation, then deletes and refetches", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Remove this passkey?")).toBeInTheDocument();
    expect(dialog).toHaveTextContent("Sessions already open stay open.");
    expect(calls.some(([m]) => m === "DELETE")).toBe(false);
    await userEvent.click(within(dialog).getByRole("button", { name: "Remove passkey" }));
    await waitFor(() => expect(calls).toContainEqual(["DELETE", "/api/v1/passkeys/p1"]));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("AC-92: keeping the passkey sends no request", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Remove" }));
    await userEvent.click(await screen.findByRole("button", { name: "Keep passkey" }));
    expect(calls.some(([m]) => m === "DELETE")).toBe(false);
  });

  it("AC-93: sessions show device, This device badge and End session only on others", async () => {
    stubApi({});
    setup();
    expect(await screen.findByText("This device")).toBeInTheDocument();
    expect(screen.getByText(/Computer · Active/)).toBeInTheDocument();
    expect(screen.getByText(/Phone · Active/)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "End session" })).toHaveLength(1);
  });

  it("AC-93/AC-97: ending a session acts immediately; 404 is treated as done", async () => {
    const calls = stubApi({
      onCall: (m, u) =>
        m === "DELETE" && u === "/api/v1/sessions/s2"
          ? json(404, { code: "not-found" })
          : undefined,
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "End session" }));
    await waitFor(() => expect(calls).toContainEqual(["DELETE", "/api/v1/sessions/s2"]));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("AC-94: Sign out of all other sessions posts end-others", async () => {
    const calls = stubApi({});
    setup();
    await userEvent.click(
      await screen.findByRole("button", { name: "Sign out of all other sessions" }),
    );
    await waitFor(() => expect(calls).toContainEqual(["POST", "/api/v1/sessions/end-others"]));
  });

  it("AC-94: only this device hides the footer button", async () => {
    stubApi({ sessionList: { items: [sessions.items[0]] } });
    setup();
    await screen.findByText("This device");
    expect(
      screen.queryByRole("button", { name: "Sign out of all other sessions" }),
    ).not.toBeInTheDocument();
  });
});
