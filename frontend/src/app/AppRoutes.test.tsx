import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { messages } from "../messages";
import { AppRoutes } from "./AppRoutes";

function open(path: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ ownerId: "o1", email: "me@example.com", theme: "system" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    ),
  );
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

const unbuilt = ["overview", "chats", "assistants", "runs", "tasks"] as const;

function mainNav() {
  return screen.getAllByRole("navigation", { name: messages.shell.mainNav })[0]!;
}

describe("section routes (AC-171, AC-172)", () => {
  it.each(unbuilt)("AC-171: /%s shows Coming soon with the section current", async (id) => {
    open(`/${id}`);
    const name = messages.shell.sections[id];

    expect(await screen.findByRole("heading", { level: 1, name })).toBeInTheDocument();
    expect(screen.getByText(messages.comingSoon.sentences[id])).toBeInTheDocument();
    expect(screen.getByText(messages.comingSoon.badge)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: messages.comingSoon.goToInbox })).toHaveAttribute(
      "href",
      "/inbox",
    );
    expect(within(mainNav()).getByRole("link", { name })).toHaveAttribute("aria-current", "page");
  });

  it("AC-172: /settings lists Profile and security linking to /profile, Settings current", async () => {
    open("/settings");

    expect(
      await screen.findByRole("heading", { level: 1, name: messages.shell.sections.settings }),
    ).toBeInTheDocument();
    const row = screen.getByRole("link", { name: new RegExp(messages.settings.profile.title) });
    expect(row).toHaveAttribute("href", "/profile");
    expect(screen.getByText(messages.settings.profile.hint)).toBeInTheDocument();
    expect(
      within(mainNav()).getByRole("link", { name: messages.shell.sections.settings }),
    ).toHaveAttribute("aria-current", "page");
  });

  it("AC-172: /profile marks Settings current", async () => {
    open("/profile");
    await screen.findByRole("heading", { level: 1, name: messages.profileSecurity.title });
    expect(
      within(mainNav()).getByRole("link", { name: messages.shell.sections.settings }),
    ).toHaveAttribute("aria-current", "page");
  });

  it("AC-171: moving from one section to another by its menu link shows the new section's page", async () => {
    open("/settings");
    await screen.findByRole("heading", { level: 1, name: messages.shell.sections.settings });

    await userEvent.click(
      within(mainNav()).getByRole("link", { name: messages.shell.sections.runs }),
    );

    expect(
      await screen.findByRole("heading", { level: 1, name: messages.shell.sections.runs }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { level: 1, name: messages.shell.sections.settings }),
    ).not.toBeInTheDocument();
  });
});
