import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "./AppRoutes";
import { FailureBoundary } from "./FailureBoundary";
import { createAppQueryClient } from "./queryClient";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const down = () => json(503, { code: "unavailable" });

function setup(entry: string | { pathname: string; state: unknown }) {
  render(
    <QueryClientProvider client={createAppQueryClient()}>
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

afterEach(() => vi.unstubAllGlobals());

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
