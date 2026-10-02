import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { failureBus } from "../../app/queryClient";
import { rememberDestination } from "../../api/destination";
import { ConfirmLinkPage } from "./ConfirmLinkPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function Where() {
  const { pathname, state } = useLocation();
  return <div data-testid="where">{`${pathname} ${JSON.stringify(state)}`}</div>;
}

function setup(hash = "#tok123") {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[`/sign-in/link${hash}`]}>
        <Routes>
          <Route path="/sign-in/link" element={<ConfirmLinkPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const urls = (m: ReturnType<typeof vi.fn>) => m.mock.calls.map((c) => (c as [string])[0]);

beforeEach(() => localStorage.clear());
afterEach(() => vi.unstubAllGlobals());

describe("SCR-08 Confirm sign-in link", () => {
  it("AC-86: opening only previews; nothing is redeemed until Continue", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { email: "me@example.com" }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    expect(
      await screen.findByRole("button", { name: "Continue as me@example.com" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Sign in to teleX" })).toBeInTheDocument();
    expect(screen.getByText("Nothing happens until you continue.")).toBeInTheDocument();
    expect(urls(fetchMock)).toEqual(["/api/v1/sign-in/link/preview"]);
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({ linkToken: "tok123" });
  });

  it("AC-86: confirming redeems with the token and time zone, token never in a query string", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, { email: "me@example.com" }))
      .mockResolvedValueOnce(json(200, { createdAccount: false }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await userEvent.click(await screen.findByRole("button", { name: /Continue as/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-in/link/redeem");
    expect(JSON.parse(init.body as string)).toEqual({ linkToken: "tok123" });
    expect(new Headers(init.headers).get("X-Telex-Time-Zone")).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
    expect((await screen.findByTestId("where")).textContent).toContain("/inbox");
  });

  it("AC-34: a created account lands on the passkey offer, not the remembered page", async () => {
    rememberDestination("/profile");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, { email: "me@example.com" }))
        .mockResolvedValueOnce(json(200, { createdAccount: true })),
    );
    setup();
    await userEvent.click(await screen.findByRole("button", { name: /Continue as/ }));
    expect((await screen.findByTestId("where")).textContent).toContain("/welcome/passkey");
  });

  it("AC-101: an existing account lands on the remembered page", async () => {
    rememberDestination("/profile");
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, { email: "me@example.com" }))
        .mockResolvedValueOnce(json(200, { createdAccount: false })),
    );
    setup();
    await userEvent.click(await screen.findByRole("button", { name: /Continue as/ }));
    expect((await screen.findByTestId("where")).textContent).toContain("/profile");
  });

  it("AC-35: preview 410 expired shows the refusal with Send a new link, without retrying", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json(410, { code: "sign-in-link-expired", email: "me@example.com" }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "This link has expired" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send a new link" })).toBeEnabled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("AC-35: preview OK but confirm after expiry is refused on confirm", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, { email: "me@example.com" }))
        .mockResolvedValueOnce(
          json(410, { code: "sign-in-link-expired", email: "me@example.com" }),
        ),
    );
    setup();
    await userEvent.click(await screen.findByRole("button", { name: /Continue as/ }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "This link has expired" }),
    ).toBeInTheDocument();
  });

  it.each([
    ["sign-in-link-used", "This link was already used"],
    ["sign-in-grant-void", "This code is no longer valid"],
  ])("410 %s shows its refusal", async (code, title) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(410, { code, email: "me@example.com" })));
    setup();
    expect(await screen.findByRole("heading", { level: 1, name: title })).toBeInTheDocument();
  });

  it("AC-35: Send a new link emails the same address and moves to Check your email", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(410, { code: "sign-in-link-expired", email: "me@example.com" }))
      .mockResolvedValueOnce(json(201, { grantId: "g2", email: "me@example.com" }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Send a new link" }));
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-in/grants");
    expect(JSON.parse(init.body as string)).toEqual({ email: "me@example.com" });
    const where = (await screen.findByTestId("where")).textContent;
    expect(where).toContain("/sign-in/check-email");
    expect(where).toContain("g2");
  });

  it("410 without email is link-unusable with a way back to sign in", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(410, { code: "sign-in-link-unknown" })));
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "This link can't be used" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Open the newest email from teleX, or sign in again."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back to sign in" })).toBeInTheDocument();
  });

  it.each([
    [410, { code: "sign-in-link-unknown" }],
    [400, { code: "validation-failed" }],
  ])("A3: confirm-time %s without a refusal shows link-unusable", async (status, body) => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, { email: "me@example.com" }))
        .mockResolvedValueOnce(json(status, body)),
    );
    setup();
    await userEvent.click(await screen.findByRole("button", { name: /Continue as/ }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "This link can't be used" }),
    ).toBeInTheDocument();
  });

  it("C8: the page itself never routes a preview 503 (the query cache does)", async () => {
    const handler = vi.spyOn(failureBus, "handler").mockImplementation(() => undefined);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(503, { code: "unavailable" })));
    setup();
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    await new Promise((r) => setTimeout(r, 20));
    expect(handler).not.toHaveBeenCalled();
    handler.mockRestore();
  });

  it("no fragment is link-unusable and calls nothing", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setup("");
    expect(
      await screen.findByRole("heading", { level: 1, name: "This link can't be used" }),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
