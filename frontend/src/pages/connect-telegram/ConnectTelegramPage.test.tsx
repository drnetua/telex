import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LinkingAttempt } from "../../api/linking";
import { ConnectTelegramPage } from "./ConnectTelegramPage";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const attempt = (over: Partial<LinkingAttempt> = {}): LinkingAttempt => ({
  step: "phone",
  origin: "inbox",
  targetLinkedAccountId: null,
  codeLength: null,
  passwordHint: null,
  ...over,
});

interface Call {
  url: string;
  method: string;
  body: unknown;
}

/** Routes fetches by "METHOD url"; a queue is consumed until its last reply, which then repeats. */
function mockApi(replies: Record<string, Response[]>) {
  const calls: Call[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? "GET";
      calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
      const queue = replies[`${method} ${url}`];
      if (!queue) return Promise.reject(new Error(`unexpected ${method} ${url}`));
      const next = queue.length > 1 ? queue.shift() : queue[0];
      return Promise.resolve(next?.clone());
    }),
  );
  return calls;
}

function setup() {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={["/connect-telegram"]}>
        <Routes>
          <Route path="/connect-telegram" element={<ConnectTelegramPage />} />
          <Route path="*" element={<div data-testid="elsewhere" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const A = "GET /api/v1/linking-attempt";
const PHONE = "POST /api/v1/linking-attempt/phone";
const CODE = "POST /api/v1/linking-attempt/code";
const RESEND = "POST /api/v1/linking-attempt/code/resend";
const PASSWORD = "POST /api/v1/linking-attempt/password";

const problem = (status: number, code: string) => json(status, { code });

afterEach(() => vi.unstubAllGlobals());

describe("SCR-02 phone step", () => {
  it("renders the step the server answers, with tel semantics (AC-01)", async () => {
    mockApi({ [A]: [json(200, attempt())] });
    setup();
    expect(await screen.findByRole("heading", { name: "Connect your Telegram" })).toBeVisible();
    const field = screen.getByLabelText("Phone number");
    expect(field).toHaveAttribute("type", "tel");
    expect(field).toHaveAttribute("autocomplete", "tel");
    expect(field).toHaveAttribute("inputmode", "tel");
    expect(screen.getByText("Include the country code.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeEnabled();
  });

  it("shows the loading state while the attempt is in flight", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => undefined)),
    );
    setup();
    expect(screen.getByRole("status")).toHaveAttribute("aria-busy", "true");
  });

  it("blocks an empty number and focuses the field", async () => {
    const calls = mockApi({ [A]: [json(200, attempt())] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Send code" }));
    const field = screen.getByLabelText("Phone number");
    expect(screen.getByText("Enter your phone number with the country code.")).toBeVisible();
    expect(field).toHaveClass("is-invalid");
    expect(field).toHaveFocus();
    expect(calls.some((c) => c.url.endsWith("/phone"))).toBe(false);
  });

  it("advances to the code step on next (AC-01)", async () => {
    const calls = mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [json(200, { outcome: "next", attempt: attempt({ step: "code", codeLength: 5 }) })],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText(/Telegram sent the code to your other devices/)).toBeVisible();
    expect(screen.getAllByRole("textbox")).toHaveLength(5);
    expect(calls.find((c) => c.url.endsWith("/phone"))?.body).toEqual({
      phoneNumber: "+380501234567",
    });
  });

  it.each([
    [
      "telegram-phone-invalid",
      "This isn't a valid phone number. Check the country code and the digits.",
    ],
    [
      "telegram-phone-unregistered",
      "No Telegram account uses this number. Create the account in the Telegram app first, then come back.",
    ],
    ["telegram-phone-banned", "Telegram has banned this number, so it can't be linked."],
  ])("says which refusal it is for %s and keeps the number (AC-107)", async (code, text) => {
    mockApi({ [A]: [json(200, attempt())], [PHONE]: [problem(422, code)] });
    setup();
    const field = await screen.findByLabelText("Phone number");
    await userEvent.type(field, "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText(text)).toBeVisible();
    expect(field).toHaveClass("is-invalid");
    expect(field).toHaveValue("+380501234567");
  });

  it("treats a 400 on the number as validation", async () => {
    mockApi({ [A]: [json(200, attempt())], [PHONE]: [problem(400, "validation-failed")] });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "12");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText("Enter your phone number with the country code.")).toBeVisible();
  });

  it("shows a busy button and read-only field while submitting, Cancel disabled", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) =>
        String(url).endsWith("/phone")
          ? new Promise(() => undefined)
          : Promise.resolve(json(200, attempt())),
      ),
    );
    setup();
    const field = await screen.findByLabelText("Phone number");
    await userEvent.type(field, "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByRole("button", { name: "Sending code" })).toBeDisabled();
    expect(field).toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: "Cancel" })).toBeDisabled();
  });

  it("toasts telegram-unavailable and keeps the step", async () => {
    mockApi({ [A]: [json(200, attempt())], [PHONE]: [problem(503, "telegram-unavailable")] });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(
      await screen.findByText("Telegram didn't answer. Check your connection and try again."),
    ).toBeVisible();
    expect(screen.getByLabelText("Phone number")).toHaveValue("+380501234567");
  });

  it("on step-mismatch refetches and renders the server's step with a toast", async () => {
    mockApi({
      [A]: [json(200, attempt()), json(200, attempt({ step: "code", codeLength: 5 }))],
      [PHONE]: [problem(409, "linking-step-mismatch")],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText(/Telegram sent the code to your other devices/)).toBeVisible();
    expect(screen.getByText("This step was already completed in another window.")).toBeVisible();
  });
});

describe("SCR-02 code step", () => {
  const codeAttempt = attempt({ step: "code", codeLength: 5 });
  const digits = () => screen.getAllByRole("textbox") as HTMLInputElement[];
  async function typeCode(value: string) {
    await userEvent.click(digits()[0] as HTMLElement);
    await userEvent.paste(value);
  }

  it("resumes on the code step after a reload (AC-109 shape)", async () => {
    mockApi({ [A]: [json(200, codeAttempt)] });
    setup();
    expect(await screen.findByText(/Telegram sent the code/)).toBeVisible();
    expect(screen.queryByLabelText("Phone number")).not.toBeInTheDocument();
  });

  it("asks for every digit before sending", async () => {
    const calls = mockApi({ [A]: [json(200, codeAttempt)] });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("123");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByText("Enter all 5 digits of the code.")).toBeVisible();
    expect(calls.some((c) => c.url.endsWith("/code"))).toBe(false);
  });

  it("goes to the password step when Telegram asks for it (AC-01)", async () => {
    mockApi({
      [A]: [json(200, codeAttempt)],
      [CODE]: [
        json(200, {
          outcome: "next",
          attempt: attempt({ step: "password", passwordHint: "first pet" }),
        }),
      ],
    });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("12345");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByLabelText("Password")).toBeVisible();
    expect(screen.getByText("Hint: first pet")).toBeVisible();
  });

  it("a wrong code is flagged, cleared, refocused and a new code can be asked (AC-02)", async () => {
    mockApi({ [A]: [json(200, codeAttempt)], [CODE]: [problem(422, "telegram-code-wrong")] });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("12345");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(
      await screen.findByText("That code is not right. Try again, or send a new code."),
    ).toBeVisible();
    await waitFor(() => expect(digits().every((d) => d.value === "")).toBe(true));
    expect(digits()[0]).toHaveFocus();
    expect(digits()[0]).toHaveClass("is-invalid");
    expect(screen.getByRole("button", { name: "Send a new code" })).toBeEnabled();
  });

  it("an expired code makes Send a new code primary (AC-02)", async () => {
    mockApi({ [A]: [json(200, codeAttempt)], [CODE]: [problem(422, "telegram-code-expired")] });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("12345");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("This code has expired. Send a new code.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Send a new code" })).toHaveClass("btn-primary");
  });

  it("resends: clears the digits and toasts", async () => {
    const calls = mockApi({ [A]: [json(200, codeAttempt)], [RESEND]: [json(200, codeAttempt)] });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("123");
    await userEvent.click(screen.getByRole("button", { name: "Send a new code" }));
    expect(await screen.findByText("Telegram sent a new code.")).toBeVisible();
    expect(digits().every((d) => d.value === "")).toBe(true);
    expect(calls.some((c) => c.url.endsWith("/code/resend"))).toBe(true);
  });

  it("keeps the step on telegram-unavailable", async () => {
    mockApi({ [A]: [json(200, codeAttempt)], [CODE]: [problem(503, "telegram-unavailable")] });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await typeCode("12345");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText(/Telegram didn't answer/)).toBeVisible();
    expect(digits()).toHaveLength(5);
  });
});

describe("SCR-02 password step", () => {
  const pw = attempt({ step: "password", passwordHint: "first pet" });

  it("has secret-safe semantics and the hint (AC-01)", async () => {
    mockApi({ [A]: [json(200, pw)] });
    setup();
    const field = await screen.findByLabelText("Password");
    expect(field).toHaveAttribute("type", "password");
    expect(field).toHaveAttribute("autocomplete", "off");
    expect(screen.getByText("Hint: first pet")).toBeVisible();
    expect(
      screen.getByText("Forgot your password? It can only be reset in the Telegram app."),
    ).toBeVisible();
  });

  it("shows no hint line when there is none", async () => {
    mockApi({ [A]: [json(200, attempt({ step: "password" }))] });
    setup();
    await screen.findByLabelText("Password");
    expect(screen.queryByText(/^Hint:/)).not.toBeInTheDocument();
  });

  it("blocks an empty password", async () => {
    mockApi({ [A]: [json(200, pw)] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Continue" }));
    expect(screen.getByText("Enter your two-step verification password.")).toBeVisible();
    expect(screen.getByLabelText("Password")).toHaveFocus();
  });

  it("a wrong password is named, hint and reset line kept, field cleared and focused (AC-106)", async () => {
    mockApi({ [A]: [json(200, pw)], [PASSWORD]: [problem(422, "telegram-password-wrong")] });
    setup();
    const field = await screen.findByLabelText("Password");
    await userEvent.type(field, "secret");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText("That password is not right.")).toBeVisible();
    expect(field).toHaveValue("");
    expect(field).toHaveFocus();
    expect(field).toHaveClass("is-invalid");
    expect(screen.getByText("Hint: first pet")).toBeVisible();
    expect(
      screen.getByText("Forgot your password? It can only be reset in the Telegram app."),
    ).toBeVisible();
  });

  it("clears the password when Telegram is unavailable", async () => {
    mockApi({ [A]: [json(200, pw)], [PASSWORD]: [problem(503, "telegram-unavailable")] });
    setup();
    const field = await screen.findByLabelText("Password");
    await userEvent.type(field, "secret");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByText(/Telegram didn't answer/)).toBeVisible();
    expect(field).toHaveValue("");
  });

  it("leaves the wizard on a finished link (AC-01)", async () => {
    mockApi({
      [A]: [json(200, pw)],
      [PASSWORD]: [json(200, { outcome: "linked", linkedAccountId: "a1", origin: "inbox" })],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
  });
});
