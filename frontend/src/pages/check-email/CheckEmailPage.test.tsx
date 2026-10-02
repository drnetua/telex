import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CheckEmailPage } from "./CheckEmailPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function Where() {
  const { pathname, state } = useLocation();
  return <div data-testid="where">{`${pathname} ${JSON.stringify(state)}`}</div>;
}

function setup(state: unknown = { grantId: "g1", email: "me@example.com" }) {
  render(
    <MemoryRouter initialEntries={[{ pathname: "/sign-in/check-email", state }]}>
      <Routes>
        <Route path="/sign-in/check-email" element={<CheckEmailPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function typeCode(code: string) {
  const first = screen.getAllByRole("textbox")[0] as HTMLElement;
  await userEvent.click(first);
  await userEvent.paste(code);
}

const signIn = () => screen.getByRole("button", { name: "Sign in" });

afterEach(() => vi.unstubAllGlobals());

describe("SCR-07 Check your email", () => {
  it("default state shows the address, six digit inputs and actions", () => {
    setup();
    expect(screen.getByRole("heading", { level: 1, name: "Check your email" })).toBeInTheDocument();
    expect(screen.getByText(/We sent a sign-in link and a 6-digit code to/)).toHaveTextContent(
      "me@example.com",
    );
    expect(screen.getAllByRole("textbox")).toHaveLength(6);
    expect(screen.getByRole("button", { name: "Send a new link" })).toBeEnabled();
    expect(
      screen.getByText("The link and the code work once and expire in 15 minutes."),
    ).toBeInTheDocument();
  });

  it("no-grant (reload) redirects to sign-in", async () => {
    setup(null);
    expect((await screen.findByTestId("where")).textContent).toContain("/sign-in");
    expect(screen.queryByRole("heading", { name: "Check your email" })).not.toBeInTheDocument();
  });

  it("pasting a code into the first digit fills all six", async () => {
    setup();
    await typeCode("482019");
    const digits = screen.getAllByRole("textbox").map((i) => (i as HTMLInputElement).value);
    expect(digits.join("")).toBe("482019");
  });

  it("validation: fewer than 6 digits sends nothing", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await typeCode("48");
    await userEvent.click(signIn());
    expect(screen.getByText("Enter the 6-digit code from the email.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("AC-82: a typed code redeems the grant with the time-zone header and signs in", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { createdAccount: true }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await typeCode("482019");
    await userEvent.click(signIn());
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-in/grants/g1/code");
    expect(JSON.parse(init.body as string)).toEqual({ code: "482019" });
    expect(new Headers(init.headers).get("X-Telex-Time-Zone")).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
    expect((await screen.findByTestId("where")).textContent).toContain("/welcome/passkey");
  });

  it("wrong code shows tries left, singular for 1", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(422, { code: "sign-in-code-wrong", attemptsLeft: 3 }))
      .mockResolvedValueOnce(json(422, { code: "sign-in-code-wrong", attemptsLeft: 1 }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await typeCode("111111");
    await userEvent.click(signIn());
    expect(await screen.findByText("That code is not right. 3 tries left.")).toBeInTheDocument();
    await userEvent.click(signIn());
    expect(await screen.findByText("That code is not right. 1 try left.")).toBeInTheDocument();
  });

  it.each([
    ["AC-35", "sign-in-link-expired", "This link has expired"],
    ["AC-84 / AC-103", "sign-in-link-used", "This link was already used"],
    ["AC-85", "sign-in-grant-void", "This code is no longer valid"],
  ])("%s: 410 %s replaces the card with a refusal", async (_ac, code, title) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(410, { code, email: "me@example.com" })));
    setup();
    await typeCode("482019");
    await userEvent.click(signIn());
    expect(await screen.findByRole("heading", { level: 1, name: title })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send a new link" })).toBeEnabled();
    expect(screen.getByText(/me@example\.com/)).toBeInTheDocument();
  });

  it("Send a new link from a refusal requests a fresh grant for the same address and shows resent", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(410, { code: "sign-in-link-expired", email: "me@example.com" }))
      .mockResolvedValueOnce(json(201, { grantId: "g2", email: "me@example.com" }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await typeCode("482019");
    await userEvent.click(signIn());
    await userEvent.click(await screen.findByRole("button", { name: "Send a new link" }));
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-in/grants");
    expect(JSON.parse(init.body as string)).toEqual({ email: "me@example.com" });
    expect(
      await screen.findByText(
        "We sent a new email to me@example.com. Only the newest email works.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: "Check your email" })).toBeInTheDocument();
  });

  it("AC-103: after resend, a code is redeemed against the newest grant only", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(201, { grantId: "g2", email: "me@example.com" }))
      .mockResolvedValueOnce(json(200, { createdAccount: false }));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Send a new link" }));
    await screen.findByText(/Only the newest email works/);
    await typeCode("482019");
    await userEvent.click(signIn());
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect((fetchMock.mock.calls[1] as [string])[0]).toBe("/api/v1/sign-in/grants/g2/code");
  });

  it("400 code-format shows the format message, not a tries-left count", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(400, { code: "code-format" })));
    setup();
    await typeCode("482019");
    await userEvent.click(signIn());
    expect(await screen.findByText("Enter the 6-digit code from the email.")).toBeInTheDocument();
    expect(screen.queryByText(/tries left|try left/)).not.toBeInTheDocument();
  });

  it("C4: the code input has a visible 'Sign-in code' label", () => {
    setup();
    expect(screen.getByText("Sign-in code")).toBeVisible();
    expect(screen.getByRole("group", { name: "Sign-in code" })).toBeInTheDocument();
  });

  it("C7: a code error is announced and linked to the digit inputs", async () => {
    vi.stubGlobal("fetch", vi.fn());
    setup();
    await userEvent.click(signIn());
    const error = screen.getByRole("alert");
    expect(error).toHaveTextContent("Enter the 6-digit code from the email.");
    const first = screen.getAllByRole("textbox")[0] as HTMLElement;
    expect(first).toHaveAttribute("aria-invalid", "true");
    expect(first).toHaveAttribute("aria-describedby", error.id);
  });

  it("C3: the resent alert carries the info-circle icon", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(json(201, { grantId: "g2", email: "me@example.com" })),
    );
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Send a new link" }));
    const alert = await screen.findByText(/We sent a new email/);
    expect(alert.closest(".alert")?.querySelector("svg.tabler-icon-info-circle")).not.toBeNull();
  });
});
