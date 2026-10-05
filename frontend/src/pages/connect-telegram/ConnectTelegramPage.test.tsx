import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FailureBoundary } from "../../app/FailureBoundary";
import { createAppQueryClient } from "../../app/queryClient";
import { linkingAttemptKey, type LinkingAttempt } from "../../api/linking";
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

function Elsewhere() {
  const location = useLocation();
  const state = location.state as { toast?: string } | null;
  return (
    <div data-testid="elsewhere" data-path={location.pathname}>
      <span data-testid="arrival">{state?.toast}</span>
    </div>
  );
}

function setup(client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/connect-telegram"]}>
        <Routes>
          <Route path="/connect-telegram" element={<ConnectTelegramPage />} />
          <Route path="*" element={<Elsewhere />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

const A = "GET /api/v1/linking-attempt";
const PHONE = "POST /api/v1/linking-attempt/phone";
const CODE = "POST /api/v1/linking-attempt/code";
const RESEND = "POST /api/v1/linking-attempt/code/resend";
const PASSWORD = "POST /api/v1/linking-attempt/password";

const problem = (status: number, code: string) => json(status, { code });

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

// The wait card ticks on a 1 s interval against the clock: both are faked, so a slow run can't tick before the test acts.
function fakeWaitClock() {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  return new Date(Date.now() + 1500).toISOString();
}

const passWait = () => act(() => vi.advanceTimersByTime(2000));

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
      [PHONE]: [json(200, attempt({ step: "code", codeLength: 5 }))],
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

  const REFUSALS = [
    ["telegram-phone-unregistered", /Telegram app first/],
    ["telegram-phone-invalid", /isn't a valid phone number/],
    ["telegram-phone-banned", /Telegram has banned this number/],
  ] as const;

  it.each(REFUSALS)(
    "%s found at the code step ends the attempt and says why (AC-107)",
    async (code, text) => {
      mockApi({ [A]: [json(200, codeAttempt)], [CODE]: [problem(422, code)] });
      setup();
      await screen.findByText(/Telegram sent the code/);
      await typeCode("12345");
      await userEvent.click(screen.getByRole("button", { name: "Continue" }));
      expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
      expect(screen.getByText(text)).toBeVisible();
      expect(screen.queryByText(/left for 15 minutes/)).toBeNull();
      expect(screen.getByRole("button", { name: "Start again" })).toBeVisible();
    },
  );

  it.each(REFUSALS)(
    "%s found on Send a new code ends the attempt and says why (AC-107)",
    async (code, text) => {
      mockApi({ [A]: [json(200, codeAttempt)], [RESEND]: [problem(422, code)] });
      setup();
      await screen.findByText(/Telegram sent the code/);
      await userEvent.click(screen.getByRole("button", { name: "Send a new code" }));
      expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
      expect(screen.getByText(text)).toBeVisible();
      expect(screen.queryByText(/left for 15 minutes/)).toBeNull();
    },
  );

  it("an unregistered number at the phone step that ended the attempt shows the ended card (AC-107)", async () => {
    mockApi({
      [A]: [json(200, attempt()), problem(404, "linking-attempt-not-found")],
      [PHONE]: [problem(422, "telegram-phone-unregistered")],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
    expect(screen.getByText(/Telegram app first/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Start again" })).toBeVisible();
  });

  it("an unregistered number with the attempt still open keeps the inline error and the field (AC-107)", async () => {
    const calls = mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [problem(422, "telegram-phone-unregistered")],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText(/Telegram app first/)).toBeVisible();
    await waitFor(() =>
      expect(calls.filter((c) => c.method === "GET" && c.url === A.slice(4))).toHaveLength(2),
    );
    expect(screen.queryByRole("heading", { name: "This linking has ended" })).toBeNull();
    expect(screen.getByLabelText("Phone number")).toBeVisible();
    expect(screen.getByText(/Telegram app first/)).toBeVisible();
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
      screen.getByText("Hint: first pet").parentElement?.querySelector(".tabler-icon-info-circle"),
    ).not.toBeNull();
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

describe("SCR-02 failures", () => {
  it("SCR-93: a 503 on the attempt load shows the unavailable page and hides the wizard", async () => {
    mockApi({ [A]: [problem(503, "unavailable")] });
    render(
      <QueryClientProvider client={createAppQueryClient()}>
        <MemoryRouter initialEntries={["/connect-telegram"]}>
          <FailureBoundary>
            <Routes>
              <Route path="/connect-telegram" element={<ConnectTelegramPage />} />
            </Routes>
          </FailureBoundary>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByRole("heading", { name: "teleX is unavailable" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Connect your Telegram" })).toBeNull();
    expect(screen.queryByLabelText("Phone number")).toBeNull();
  });

  it("AC-109: a load failure shows an inline state with what failed, Try again and Back (no Toast to dismiss)", async () => {
    mockApi({ [A]: [problem(418, "teapot")] });
    setup();
    expect(await screen.findByText("We couldn't load your Telegram linking.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Dismiss" })).toBeNull();
    expect(screen.getByRole("button", { name: "Try again" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Back" })).toBeVisible();
    expect(document.querySelector('[aria-busy="true"]')).toBeNull();
  });

  it("AC-109: Back from a load failure leaves the wizard for the origin", async () => {
    mockApi({ [A]: [problem(418, "teapot")] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Back" }));
    expect(await screen.findByTestId("elsewhere")).toHaveAttribute("data-path", "/inbox");
  });

  it("offers Try again on a load failure that refetches the attempt", async () => {
    const calls = mockApi({ [A]: [problem(418, "teapot"), json(200, attempt())] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));
    expect(await screen.findByLabelText("Phone number")).toBeVisible();
    expect(calls.filter((c) => c.url.endsWith("/linking-attempt"))).toHaveLength(2);
    expect(screen.queryByText("We couldn't load your Telegram linking.")).toBeNull();
  });

  it("keeps the step on screen when a later refetch fails", async () => {
    mockApi({ [A]: [json(200, attempt()), problem(503, "unavailable")] });
    const client = setup();
    expect(await screen.findByLabelText("Phone number")).toBeVisible();
    await client.refetchQueries({ queryKey: ["linking-attempt"] });
    expect(screen.getByLabelText("Phone number")).toBeVisible();
  });

  it("gives an error toast for a failure no screen state covers (AC-01)", async () => {
    mockApi({ [A]: [json(200, attempt())], [PHONE]: [problem(418, "teapot")] });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText("Something went wrong. Try again.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Send code" })).toBeEnabled();
  });

  it("gives an error toast when the step-mismatch refetch fails", async () => {
    mockApi({
      [A]: [json(200, attempt()), problem(418, "teapot")],
      [PHONE]: [problem(409, "linking-step-mismatch")],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
    expect(await screen.findByText("Something went wrong. Try again.")).toBeVisible();
  });

  it("caches the linked accounts so the Inbox's first render has the new account (AC-01)", async () => {
    mockApi({
      [A]: [json(200, attempt({ step: "password" }))],
      [PASSWORD]: [json(200, { outcome: "linked", linkedAccountId: "a1", origin: "inbox" })],
      "GET /api/v1/linked-accounts": [
        json(200, {
          items: [
            {
              id: "a1",
              displayName: "Ann",
              phone: { countryCode: "380", lastDigits: "42" },
              state: "connected",
              chatSync: null,
              linkedAt: "2026-01-01T00:00:00Z",
            },
          ],
        }),
      ],
    });
    const client = setup();
    await userEvent.type(await screen.findByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
    expect(client.getQueryData(["linked-accounts"])).toHaveLength(1);
  });
});

describe("SCR-02 outcomes", () => {
  const LIST = "GET /api/v1/linked-accounts";
  const START = "POST /api/v1/linking-attempt";
  const CANCEL = "DELETE /api/v1/linking-attempt";
  const submitPhone = async () => {
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
  };
  const phoneReply = (reply: Response) => ({ [A]: [json(200, attempt())], [PHONE]: [reply] });
  const account = (id: string, displayName: string) =>
    json(200, {
      items: [
        {
          id,
          displayName,
          phone: { countryCode: "380", lastDigits: "67" },
          state: "connected",
          chatSync: { chatsSynced: 0, chatsTotal: null, completedAt: null },
          linkedAt: "2026-01-01T00:00:00Z",
        },
      ],
    });

  it("shows the wait state with the time from retryAt on 429", async () => {
    const retryAt = new Date(Date.now() + 90_000).toISOString();
    mockApi(phoneReply(json(429, { code: "telegram-wait-required", retryAt })));
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "Too many attempts" })).toBeVisible();
    expect(
      screen.getByText(/You can try again at \d\d:\d\d, in 1:/, {
        selector: "[aria-hidden='true']",
      }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
  });

  it.each([
    ["telegram-account-owned-by-another-owner", {}, "This account is linked elsewhere", "Back"],
    ["telegram-account-already-linked", {}, "Already linked", "Back"],
    ["linked-account-limit-reached", { limit: 3 }, "Account limit reached", "Open Accounts"],
  ])("refuses with %s (AC-04, AC-108, AC-115)", async (code, extras, title, button) => {
    mockApi(phoneReply(json(409, { code, ...extras })));
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: title })).toBeVisible();
    expect(screen.getByRole("button", { name: button })).toBeEnabled();
  });

  it("states the limit and that teleX signed out (AC-115)", async () => {
    mockApi(phoneReply(json(409, { code: "linked-account-limit-reached", limit: 3 })));
    setup();
    await submitPhone();
    expect(
      await screen.findByText(
        "You've linked 3 accounts, the most this installation allows. teleX has signed out of this one. Unlink an account to free a place.",
      ),
    ).toBeVisible();
  });

  it("names the expected account on a mismatch and returns to Accounts (AC-117)", async () => {
    mockApi({
      [A]: [json(200, attempt({ targetLinkedAccountId: "a1" }))],
      [LIST]: [account("a1", "Work")],
      [PHONE]: [json(409, { code: "telegram-account-mismatch" })],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "A different account" })).toBeVisible();
    expect(screen.getByText(/not Work\. teleX has signed out of it/)).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Back to Accounts" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
  });

  it("shows the ended state on 404 and starts again at the phone step (AC-109)", async () => {
    const calls = mockApi({
      ...phoneReply(problem(404, "linking-attempt-not-found")),
      [START]: [json(201, attempt())],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
    expect(
      screen.getByText("It was cancelled, finished in another window, or left for 15 minutes."),
    ).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Start again" }));
    expect(await screen.findByLabelText("Phone number")).toHaveValue("");
    expect(calls.find((c) => c.method === "POST" && c.url === START.slice(5))?.body).toEqual({
      origin: "inbox",
    });
  });

  it("starts again without the target when the account was unlinked meanwhile (AC-117)", async () => {
    const calls = mockApi({
      [A]: [json(200, attempt({ targetLinkedAccountId: "a1" }))],
      [PHONE]: [problem(404, "linking-attempt-not-found")],
      [START]: [problem(404, "not-found"), json(201, attempt())],
    });
    setup();
    await submitPhone();
    await userEvent.click(await screen.findByRole("button", { name: "Start again" }));
    expect(await screen.findByLabelText("Phone number")).toHaveValue("");
    const starts = calls.filter((c) => c.method === "POST" && c.url === START.slice(5));
    expect(starts.map((c) => c.body)).toEqual([
      { origin: "inbox", targetLinkedAccountId: "a1" },
      { origin: "inbox" },
    ]);
  });

  it("AC-122: a fresh attempt landing in the cache replaces an outcome card with its step", async () => {
    mockApi(phoneReply(problem(404, "linking-attempt-not-found")));
    const client = setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
    act(() => client.setQueryData(linkingAttemptKey, attempt({ origin: "accounts" })));
    expect(await screen.findByLabelText("Phone number")).toBeVisible();
    expect(screen.queryByRole("heading", { name: "This linking has ended" })).toBeNull();
  });

  it("AC-122: an identical fresh attempt put in the cache still replaces the outcome card", async () => {
    mockApi(phoneReply(problem(404, "linking-attempt-not-found")));
    const client = setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "This linking has ended" })).toBeVisible();
    // Deep-equal to the cached attempt: structural sharing keeps the old reference.
    act(() => client.setQueryData(linkingAttemptKey, attempt()));
    expect(await screen.findByLabelText("Phone number")).toBeVisible();
    expect(screen.queryByRole("heading", { name: "This linking has ended" })).toBeNull();
  });

  it("toasts the start refusal and stays on the ended state", async () => {
    mockApi({
      [A]: [problem(404, "linking-attempt-not-found")],
      [START]: [problem(503, "telegram-linking-not-set-up")],
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Start again" }));
    expect(await screen.findByText(/Telegram linking isn't set up/)).toBeVisible();
    expect(screen.getByRole("heading", { name: "This linking has ended" })).toBeVisible();
  });

  it("Back on the ended state leaves", async () => {
    mockApi({ [A]: [problem(404, "linking-attempt-not-found")] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Back" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
  });

  it("cancels and leaves without a message (AC-109)", async () => {
    mockApi({ [A]: [json(200, attempt())], [CANCEL]: [new Response(null, { status: 204 })] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
    expect(screen.getByTestId("arrival")).toBeEmptyDOMElement();
  });

  it("shows Cancelling while the cancel is in flight", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((_url: string, init?: RequestInit) =>
        init?.method === "DELETE"
          ? new Promise(() => undefined)
          : Promise.resolve(json(200, attempt())),
      ),
    );
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Cancel" }));
    expect(await screen.findByRole("button", { name: "Cancelling" })).toBeDisabled();
  });

  it.each([
    ["linked", "Anna is connected. teleX is syncing its chats."],
    ["signed-in-again", "Anna is connected again."],
  ])("carries the %s toast to the origin (AC-117)", async (outcome, text) => {
    mockApi({
      [A]: [json(200, attempt({ step: "password" }))],
      [PASSWORD]: [json(200, { outcome, linkedAccountId: "a1", origin: "inbox" })],
      [LIST]: [account("a1", "Anna")],
    });
    setup();
    await userEvent.type(await screen.findByLabelText("Password"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByTestId("elsewhere")).toBeInTheDocument();
    expect(screen.getByTestId("arrival")).toHaveTextContent(text);
  });
});

describe("SCR-02 outcome cards take focus (AC-107, AC-109)", () => {
  const START = "POST /api/v1/linking-attempt";
  const submitPhone = async () => {
    await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
    await userEvent.click(screen.getByRole("button", { name: "Send code" }));
  };
  const codeAttempt = attempt({ step: "code", codeLength: 5 });
  const ended = "This linking has ended";

  it("moves focus to the ended card that names a code refusal (AC-107)", async () => {
    mockApi({
      [A]: [json(200, codeAttempt)],
      [CODE]: [problem(422, "telegram-phone-unregistered")],
    });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await userEvent.click(screen.getAllByRole("textbox")[0] as HTMLElement);
    await userEvent.paste("12345");
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
  });

  it("moves focus to the ended card that names a resend refusal (AC-107)", async () => {
    mockApi({
      [A]: [json(200, codeAttempt)],
      [RESEND]: [problem(422, "telegram-phone-banned")],
    });
    setup();
    await screen.findByText(/Telegram sent the code/);
    await userEvent.click(screen.getByRole("button", { name: "Send a new code" }));
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
  });

  it("moves focus to the ended card after an unregistered number ended the attempt (AC-107)", async () => {
    mockApi({
      [A]: [json(200, attempt()), problem(404, "linking-attempt-not-found")],
      [PHONE]: [problem(422, "telegram-phone-unregistered")],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
  });

  it("moves focus to the wait card on 429 (AC-02)", async () => {
    const retryAt = new Date(Date.now() + 90_000).toISOString();
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [json(429, { code: "telegram-wait-required", retryAt })],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "Too many attempts" })).toHaveFocus();
  });

  it("moves focus to the refused card on 409 (AC-108)", async () => {
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [json(409, { code: "telegram-account-already-linked" })],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: "Already linked" })).toHaveFocus();
  });

  it("moves focus to the ended card on a 404 (AC-109)", async () => {
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [problem(404, "linking-attempt-not-found")],
    });
    setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
  });

  it("moves focus to the ended card when the load answers 404 (AC-109)", async () => {
    mockApi({ [A]: [problem(404, "linking-attempt-not-found")], [START]: [json(201, attempt())] });
    setup();
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
  });

  it("moves focus to the phone step heading after Start again on the ended card (AC-109)", async () => {
    mockApi({
      [A]: [problem(404, "linking-attempt-not-found")],
      [START]: [json(201, attempt())],
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Start again" }));
    expect(await screen.findByRole("heading", { name: "Connect your Telegram" })).toHaveFocus();
  });

  it("leaves focus off the step heading when the code step replaces the phone step (AC-109)", async () => {
    mockApi({
      [A]: [problem(404, "linking-attempt-not-found")],
      [START]: [json(201, attempt())],
      [PHONE]: [json(200, codeAttempt)],
    });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Start again" }));
    const heading = await screen.findByRole("heading", { name: "Connect your Telegram" });
    expect(heading).toHaveFocus();
    await submitPhone();
    await screen.findByText(/Telegram sent the code to your other devices/);
    expect(heading).not.toHaveFocus();
  });

  it("moves focus to the step heading after load-failed Try again (AC-109)", async () => {
    mockApi({ [A]: [problem(418, "teapot"), json(200, attempt())] });
    setup();
    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("heading", { name: "Connect your Telegram" })).toHaveFocus();
  });

  const ME = "GET /api/v1/me";
  const me = (timeZone: string | null) =>
    json(200, { ownerId: "o1", email: "ann@example.com", timeZone });
  const waitReply = (retryAt: string, retryAfter: number) =>
    new Response(JSON.stringify({ code: "telegram-wait-required", retryAt }), {
      status: 429,
      headers: { "Content-Type": "application/problem+json", "Retry-After": String(retryAfter) },
    });

  it("shows the retry time in the Owner's saved zone, not the device's (AC-02)", async () => {
    vi.useFakeTimers({
      toFake: ["setInterval", "clearInterval", "Date"],
      now: new Date("2026-10-05T02:58:00Z"),
    });
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [waitReply("2026-10-05T03:00:00Z", 120)],
      [ME]: [me("Asia/Tokyo")],
    });
    setup();
    await submitPhone();
    expect(
      await screen.findByText("Telegram asks you to wait. You can try again at 12:00, in 2:00.", {
        selector: "[aria-hidden='true']",
      }),
    ).toBeInTheDocument();
  });

  it("counts down from Retry-After whatever the device clock says (AC-02)", async () => {
    // the device clock runs 10 minutes ahead of the server's
    vi.useFakeTimers({
      toFake: ["setInterval", "clearInterval", "Date"],
      now: new Date("2026-10-05T03:08:00Z"),
    });
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [waitReply("2026-10-05T03:00:00Z", 120)],
      [ME]: [me("Asia/Tokyo")],
    });
    setup();
    await submitPhone();
    expect(
      await screen.findByText("Telegram asks you to wait. You can try again at 12:00, in 2:00.", {
        selector: "[aria-hidden='true']",
      }),
    ).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(119_000));
    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1000));
    expect(
      screen.getByText("You can try again now.", { selector: "[aria-hidden='false']" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start again" })).toBeInTheDocument();
  });

  it("does not turn the focused Back into Start again at 0:00 on the wait card (AC-02)", async () => {
    const retryAt = fakeWaitClock();
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [json(429, { code: "telegram-wait-required", retryAt })],
    });
    setup();
    await submitPhone();
    await screen.findByRole("heading", { name: "Too many attempts" });
    const back = screen.getByRole("button", { name: "Back" });
    back.focus();
    passWait();
    const again = screen.getByRole("button", { name: "Start again" });
    expect(again).not.toHaveFocus();
    expect(screen.getByRole("heading", { name: "Too many attempts" })).toHaveFocus();
  });

  it("leaves focus alone at 0:00 when Back was not focused (AC-02)", async () => {
    const retryAt = fakeWaitClock();
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [json(429, { code: "telegram-wait-required", retryAt })],
    });
    setup();
    await submitPhone();
    const heading = await screen.findByRole("heading", { name: "Too many attempts" });
    (document.activeElement as HTMLElement | null)?.blur();
    passWait();
    expect(screen.getByRole("button", { name: "Start again" })).toBeInTheDocument();
    expect(heading).not.toHaveFocus();
  });

  it("leaves focus off the step heading on the first load (AC-109)", async () => {
    mockApi({ [A]: [json(200, attempt())] });
    setup();
    const heading = await screen.findByRole("heading", { name: "Connect your Telegram" });
    await screen.findByLabelText("Phone number");
    expect(heading).not.toHaveFocus();
  });

  it("moves focus to the step heading when the banner's Sign in again clears an outcome card (AC-122)", async () => {
    mockApi({
      [A]: [json(200, attempt())],
      [PHONE]: [problem(404, "linking-attempt-not-found")],
    });
    const client = setup();
    await submitPhone();
    expect(await screen.findByRole("heading", { name: ended })).toHaveFocus();
    act(() => client.setQueryData(linkingAttemptKey, attempt({ origin: "accounts" })));
    expect(await screen.findByRole("heading", { name: "Connect your Telegram" })).toHaveFocus();
  });

  it.each([
    ["the ended card from the first load", problem(404, "linking-attempt-not-found"), ended],
    ["the load-failed card", problem(418, "teapot"), "We couldn't load your Telegram linking."],
  ])(
    "moves focus to the step heading when the banner's Sign in again replaces %s (AC-122)",
    async (_, failure, card) => {
      mockApi({ [A]: [failure] });
      const client = setup();
      await screen.findByRole("heading", { name: card });
      act(() => client.setQueryData(linkingAttemptKey, attempt({ origin: "accounts" })));
      expect(await screen.findByRole("heading", { name: "Connect your Telegram" })).toHaveFocus();
    },
  );

  it("moves focus to the load-failed card (AC-109)", async () => {
    mockApi({ [A]: [problem(418, "teapot")] });
    setup();
    expect(
      await screen.findByRole("heading", { name: "We couldn't load your Telegram linking." }),
    ).toHaveFocus();
  });
});

describe("SCR-02 session end (AC-110)", () => {
  it.each([
    [
      "the attempt load",
      {
        [A]: [problem(401, "session-ended")],
        "GET /api/v1/linked-accounts": [json(200, { items: [] })],
      },
      false,
    ],
    [
      "a step submit",
      {
        [A]: [json(200, attempt())],
        "GET /api/v1/linked-accounts": [json(200, { items: [] })],
        [PHONE]: [problem(401, "session-ended")],
      },
      true,
    ],
  ])(
    "a 401 session-ended on %s reaches the session-ended screen",
    async (_name, replies, submit) => {
      mockApi(replies);
      render(
        <QueryClientProvider client={createAppQueryClient()}>
          <MemoryRouter initialEntries={["/connect-telegram"]}>
            <FailureBoundary>
              <Routes>
                <Route path="/connect-telegram" element={<ConnectTelegramPage />} />
                <Route path="/session-ended" element={<h1>Session ended</h1>} />
              </Routes>
            </FailureBoundary>
          </MemoryRouter>
        </QueryClientProvider>,
      );
      if (submit) {
        await userEvent.type(await screen.findByLabelText("Phone number"), "+380501234567");
        await userEvent.click(screen.getByRole("button", { name: "Send code" }));
      }
      expect(await screen.findByRole("heading", { name: "Session ended" })).toBeVisible();
      expect(screen.queryByLabelText("Phone number")).toBeNull();
    },
  );
});
