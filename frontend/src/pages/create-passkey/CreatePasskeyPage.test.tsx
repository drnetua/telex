import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CreatePasskeyPage } from "./CreatePasskeyPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function Where() {
  const { pathname } = useLocation();
  return <div data-testid="where">{pathname}</div>;
}

function setup(state: unknown = { createdAccount: true }) {
  render(
    <MemoryRouter initialEntries={[{ pathname: "/welcome/passkey", state }]}>
      <Routes>
        <Route path="/welcome/passkey" element={<CreatePasskeyPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
}

const credential = {
  id: "cred",
  type: "public-key",
  rawId: new ArrayBuffer(4),
  response: { attestationObject: new ArrayBuffer(4), clientDataJSON: new ArrayBuffer(4) },
  getClientExtensionResults: () => ({}),
  toJSON: () => ({ id: "cred", type: "public-key", response: {} }),
};

const options = {
  challenge: "AAAA",
  rp: { name: "teleX" },
  user: { id: "AAAA", name: "me", displayName: "me" },
};

function stubCapable(create: () => Promise<unknown>) {
  vi.stubGlobal("PublicKeyCredential", {
    isUserVerifyingPlatformAuthenticatorAvailable: () => Promise.resolve(true),
  });
  Object.defineProperty(navigator, "credentials", { value: { create }, configurable: true });
}

afterEach(() => vi.unstubAllGlobals());

describe("SCR-09 Create a passkey", () => {
  it("default state offers Create a passkey and Not now", async () => {
    stubCapable(() => Promise.resolve(credential));
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "Sign in faster next time" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Create a passkey to sign in with your fingerprint, face or screen lock instead of an email.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create a passkey" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Not now" })).toBeEnabled();
  });

  it("no-flag: opened without router state redirects to the Inbox", async () => {
    stubCapable(() => Promise.resolve(credential));
    setup(null);
    expect((await screen.findByTestId("where")).textContent).toBe("/inbox");
  });

  it("AC-91: Not now goes to the Inbox and registers nothing", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    stubCapable(() => Promise.resolve(credential));
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Not now" }));
    expect((await screen.findByTestId("where")).textContent).toBe("/inbox");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("AC-90: a browser without passkeys explains and continues to the Inbox", async () => {
    vi.stubGlobal("PublicKeyCredential", undefined);
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "Passkeys aren't available here" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "This browser doesn't support passkeys. You can add one later from another device in Profile and security.",
      ),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect((await screen.findByTestId("where")).textContent).toBe("/inbox");
  });

  it("AC-89: confirming fetches options, registers the credential and goes to the Inbox", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, options))
      .mockResolvedValueOnce(json(200, { success: true }));
    vi.stubGlobal("fetch", fetchMock);
    stubCapable(() => Promise.resolve(credential));
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Create a passkey" }));
    expect((await screen.findByTestId("where")).textContent).toBe("/inbox");
    const calls = fetchMock.mock.calls as [string, RequestInit][];
    expect(calls.map((c) => c[0])).toEqual(["/webauthn/register/options", "/webauthn/register"]);
    expect(calls[1]?.[1].method).toBe("POST");
    expect(JSON.parse(calls[1]?.[1].body as string)).toHaveProperty("publicKey.credential");
  });

  it("waiting: busy label while the device check runs, Not now disabled", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, options)));
    stubCapable(() => new Promise(() => {}));
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Create a passkey" }));
    expect(await screen.findByRole("button", { name: "Waiting for your device" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Not now" })).toBeDisabled();
  });

  it("AC-105: cancelling the device check stays on the step with a failure and Try again", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, options));
    vi.stubGlobal("fetch", fetchMock);
    stubCapable(() => Promise.reject(new DOMException("cancelled", "NotAllowedError")));
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Create a passkey" }));
    expect(
      await screen.findByText("No passkey was created. Try again, or choose Not now."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Not now" })).toBeEnabled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId("where")).not.toBeInTheDocument();
  });

  it("AC-105: 400 passkey-registration-failed shows the same failure; Not now still leaves", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, options))
        .mockResolvedValueOnce(json(400, { code: "passkey-registration-failed" })),
    );
    stubCapable(() => Promise.resolve(credential));
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Create a passkey" }));
    expect(
      await screen.findByText("No passkey was created. Try again, or choose Not now."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Not now" }));
    expect((await screen.findByTestId("where")).textContent).toBe("/inbox");
  });
});
