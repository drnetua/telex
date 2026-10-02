import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SignInPage } from "./SignInPage";

function Where() {
  const { pathname, state } = useLocation();
  return <div data-testid="where">{`${pathname} ${JSON.stringify(state)}`}</div>;
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

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

afterEach(() => vi.unstubAllGlobals());

describe("SCR-01 Sign in, email part", () => {
  it("default state shows copy and the email field", () => {
    setup();
    expect(screen.getByRole("heading", { level: 1, name: "Sign in to teleX" })).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("type", "email");
    expect(screen.getByLabelText("Email")).toHaveAttribute("autocomplete", "email");
    expect(screen.getByRole("button", { name: "Email me a sign-in link" })).toBeEnabled();
    expect(
      screen.getByText("No passwords. The link works once and expires in 15 minutes."),
    ).toBeInTheDocument();
  });

  it.each(["", "me", "me@localhost", "@example.com", "me@example"])(
    "AC-83: %j is refused with a message and no request",
    async (value) => {
      const fetchMock = vi.fn();
      vi.stubGlobal("fetch", fetchMock);
      setup();
      if (value) await userEvent.type(screen.getByLabelText("Email"), value);
      await userEvent.click(screen.getByRole("button", { name: "Email me a sign-in link" }));
      expect(
        screen.getByText("Enter a complete email address, like me@example.com."),
      ).toBeInTheDocument();
      expect(screen.getByLabelText("Email")).toHaveClass("is-invalid");
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("server email-incomplete shows the same message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(400, { code: "validation-failed" })));
    setup();
    await userEvent.type(screen.getByLabelText("Email"), "me@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Email me a sign-in link" }));
    expect(
      await screen.findByText("Enter a complete email address, like me@example.com."),
    ).toBeInTheDocument();
  });

  it("submitting is busy with a read-only field, success goes to Check your email with grant state", async () => {
    let resolve!: (r: Response) => void;
    const fetchMock = vi.fn(() => new Promise<Response>((r) => (resolve = r)));
    vi.stubGlobal("fetch", fetchMock);
    setup();
    await userEvent.type(screen.getByLabelText("Email"), "me@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Email me a sign-in link" }));
    expect(screen.getByRole("button", { name: "Sending email" })).toBeDisabled();
    expect(screen.getByLabelText("Email")).toHaveAttribute("readonly");
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/v1/sign-in/grants");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ email: "me@example.com" });
    resolve(json(201, { grantId: "g1", email: "me@example.com" }));
    const where = await screen.findByTestId("where");
    expect(where.textContent).toContain("/sign-in/check-email");
    expect(where.textContent).toContain("g1");
    expect(where.textContent).toContain("me@example.com");
  });
});
