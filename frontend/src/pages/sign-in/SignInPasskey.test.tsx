import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { rememberDestination } from "../../api/destination";
import { SignInPage } from "./SignInPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function Where() {
  const { pathname } = useLocation();
  return <div data-testid="where">{pathname}</div>;
}

function setup() {
  render(
    <MemoryRouter initialEntries={["/sign-in"]}>
      <Routes>
        <Route path="/sign-in" element={<SignInPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
}

const requestOptions = { challenge: "AAAA", allowCredentials: [], rpId: "localhost" };
const assertion = {
  id: "cred",
  type: "public-key",
  rawId: new ArrayBuffer(4),
  response: {
    authenticatorData: new ArrayBuffer(4),
    clientDataJSON: new ArrayBuffer(4),
    signature: new ArrayBuffer(4),
    userHandle: new ArrayBuffer(4),
  },
  getClientExtensionResults: () => ({}),
  toJSON: () => ({ id: "cred", type: "public-key", response: {} }),
};

function stubGet(get: () => Promise<unknown>) {
  vi.stubGlobal("PublicKeyCredential", {
    isUserVerifyingPlatformAuthenticatorAvailable: () => Promise.resolve(true),
  });
  Object.defineProperty(navigator, "credentials", { value: { get }, configurable: true });
}

afterEach(() => vi.unstubAllGlobals());

describe("SCR-01 Sign in with a passkey", () => {
  it("AC-89: one touch signs in without an email and lands on the remembered page", async () => {
    rememberDestination("/profile");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, requestOptions))
      .mockResolvedValueOnce(json(200, { createdAccount: false }));
    vi.stubGlobal("fetch", fetchMock);
    stubGet(() => Promise.resolve(assertion));
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Sign in with a passkey" }));
    expect((await screen.findByTestId("where")).textContent).toBe("/profile");
    const calls = fetchMock.mock.calls as [string, RequestInit][];
    expect(calls.map((c) => c[0])).toEqual(["/webauthn/authenticate/options", "/login/webauthn"]);
    expect(new Headers(calls[1]?.[1].headers).get("X-Telex-Time-Zone")).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
  });

  it("passkey-waiting: busy label while the device check runs", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, requestOptions)));
    stubGet(() => new Promise(() => {}));
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Sign in with a passkey" }));
    expect(await screen.findByRole("button", { name: "Waiting for your device" })).toBeDisabled();
  });

  it("passkey-failed: 401 passkey-rejected shows the alert and the email form still works", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json(200, requestOptions))
        .mockResolvedValueOnce(json(401, { code: "passkey-rejected" })),
    );
    stubGet(() => Promise.resolve(assertion));
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Sign in with a passkey" }));
    expect(
      await screen.findByText("That passkey didn't work. Sign in with your email instead."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Email me a sign-in link" })).toBeEnabled();
  });

  it("a cancelled device check silently returns to default", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(200, requestOptions)));
    stubGet(() => Promise.reject(new DOMException("cancelled", "NotAllowedError")));
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Sign in with a passkey" }));
    expect(await screen.findByRole("button", { name: "Sign in with a passkey" })).toBeEnabled();
    expect(screen.queryByText(/didn't work/)).not.toBeInTheDocument();
    expect(screen.queryByTestId("where")).not.toBeInTheDocument();
  });
});
