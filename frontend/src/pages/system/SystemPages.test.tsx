import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { NotFoundPage } from "./NotFoundPage";
import { SessionEndedPage } from "./SessionEndedPage";
import { UnavailablePage } from "./UnavailablePage";

const wrap = (ui: React.ReactElement, qc = new QueryClient()) =>
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );

describe("SCR-91 Page not found (AC-102)", () => {
  it("shows the message and a Go to Inbox link", () => {
    wrap(<NotFoundPage />);
    expect(screen.getByRole("heading", { level: 1, name: "Page not found" })).toBeInTheDocument();
    expect(screen.getByText("This page doesn't exist in teleX.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to Inbox" })).toHaveAttribute("href", "/inbox");
  });
});

describe("SCR-92 Session ended (AC-96, AC-93)", () => {
  it("shows the message, links to sign-in and clears the query cache on entry", async () => {
    const qc = new QueryClient();
    qc.setQueryData(["me"], { email: "a@b.c" });
    wrap(<SessionEndedPage />, qc);
    expect(screen.getByRole("heading", { level: 1, name: "Session ended" })).toBeInTheDocument();
    expect(
      screen.getByText("Your session has ended. Sign in again to continue."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in again" })).toHaveAttribute("href", "/sign-in");
    await waitFor(() => expect(qc.getQueryData(["me"])).toBeUndefined());
  });
});

describe("SCR-93 teleX is unavailable (AC-102)", () => {
  it("default state offers Retry", () => {
    wrap(<UnavailablePage onRetry={vi.fn()} />);
    expect(
      screen.getByRole("heading", { level: 1, name: "teleX is unavailable" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("teleX didn't answer. Check your connection, then try again."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry" })).toBeEnabled();
  });

  it("retrying shows a busy, disabled Retrying button while the request repeats", async () => {
    let resolve!: () => void;
    const onRetry = vi.fn(() => new Promise<void>((r) => (resolve = r)));
    wrap(<UnavailablePage onRetry={onRetry} />);
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Retrying" })).toBeDisabled();
    resolve();
  });

  it("retry-failed shows 'Still no answer.' in a polite live region", async () => {
    const onRetry = vi.fn().mockRejectedValue(new Error("down"));
    wrap(<UnavailablePage onRetry={onRetry} />);
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));
    const note = await screen.findByText("Still no answer.");
    expect(note.closest("[aria-live]")).toHaveAttribute("aria-live", "polite");
    expect(screen.getByRole("button", { name: "Retry" })).toBeEnabled();
  });
});
