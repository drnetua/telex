import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppQueryClient } from "../../app/queryClient";
import { isShellActive, resetConnectivity } from "../connectivity";
import { sections } from "../sections";
import { AppShell } from "./AppShell";

type Listener = () => void;

function stubWidth(phone: boolean) {
  const listeners = new Set<Listener>();
  const state = { phone };
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation(() => ({
      get matches() {
        return state.phone;
      },
      addEventListener: (_: string, l: Listener) => listeners.add(l),
      removeEventListener: (_: string, l: Listener) => listeners.delete(l),
    })),
  );
  return (next: boolean) => {
    state.phone = next;
    listeners.forEach((l) => l());
  };
}

function setup(path = "/inbox") {
  const client = createAppQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/sign-in" element={<h1>Sign in page</h1>} />
          <Route
            path="*"
            element={
              <AppShell email="me@example.com">
                <p>page body</p>
              </AppShell>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

afterEach(() => vi.unstubAllGlobals());

const labels = (nav: HTMLElement) =>
  within(nav)
    .getAllByRole("link")
    .map((l) => l.textContent?.trim());

describe("AppShell (AC-170, AC-43, AC-172)", () => {
  it("registry lists the seven sections in app-map order", () => {
    expect(sections.map((s) => s.id)).toEqual([
      "overview",
      "inbox",
      "chats",
      "assistants",
      "runs",
      "tasks",
      "settings",
    ]);
  });

  it("AC-170: desktop side menu lists seven sections in order with real links", () => {
    stubWidth(false);
    setup("/inbox");
    const nav = screen.getByRole("navigation", { name: "Main" });
    expect(labels(nav)).toEqual([
      "Overview",
      "Inbox",
      "Chats",
      "Assistants",
      "Runs",
      "Tasks",
      "Settings",
    ]);
    expect(within(nav).getByRole("link", { name: "Runs" })).toHaveAttribute("href", "/runs");
    expect(screen.getByText("page body")).toBeInTheDocument();
    expect(screen.getByText("me@example.com")).toBeInTheDocument();
  });

  it("AC-170: current section carries aria-current and a bold label, others do not", () => {
    stubWidth(false);
    setup("/chats");
    const nav = screen.getByRole("navigation", { name: "Main" });
    const current = within(nav).getByRole("link", { name: "Chats" });
    expect(current).toHaveAttribute("aria-current", "page");
    expect(current).toHaveClass("active");
    expect(within(current).getByText("Chats")).toHaveClass("fw-bold");
    expect(within(nav).getByRole("link", { name: "Inbox" })).not.toHaveAttribute("aria-current");
  });

  it("AC-43, AC-174: phone bar is Inbox, Chats, Assistants, Tasks and More, with the pulse's Inbox counter", async () => {
    stubWidth(true);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ inboxCount: 4, conditions: [] })),
    );
    setup("/inbox");
    await waitFor(() => expect(screen.getByRole("link", { name: /Inbox/ })).toHaveTextContent("4"));
    const nav = screen.getByRole("navigation", { name: "Main" });
    expect(
      within(nav)
        .getAllByRole("link")
        .map((l) => l.getAttribute("href")),
    ).toEqual(["/inbox", "/chats", "/assistants", "/tasks"]);
    expect(within(nav).getByRole("button", { name: "More" })).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: /Inbox/ })).toHaveTextContent("4");
    expect(within(nav).queryByRole("link", { name: "Overview" })).toBeNull();
  });

  it("AC-43: More lists Overview, Runs and Settings; Escape closes it and returns focus", async () => {
    stubWidth(true);
    setup("/inbox");
    const more = screen.getByRole("button", { name: "More" });
    await userEvent.click(more);
    const sheet = screen.getByRole("dialog", { name: "More" });
    expect(labels(sheet)).toEqual(["Overview", "Runs", "Settings"]);
    expect(within(sheet).getByRole("button", { name: "Sign out" })).toBeInTheDocument();
    expect(sheet.contains(document.activeElement)).toBe(true);
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "More" })).toBeNull();
    expect(more).toHaveFocus();
  });

  it("a section under More marks More current; choosing one closes the sheet", async () => {
    stubWidth(true);
    setup("/runs");
    const more = screen.getByRole("button", { name: "More" });
    expect(more).toHaveAttribute("aria-current", "page");
    await userEvent.click(more);
    const sheet = screen.getByRole("dialog", { name: "More" });
    expect(within(sheet).getByRole("link", { name: "Runs" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await userEvent.click(within(sheet).getByRole("link", { name: "Settings" }));
    expect(screen.queryByRole("dialog", { name: "More" })).toBeNull();
  });

  it("phone header shows the current section name; crossing 768 px closes More", async () => {
    const resize = stubWidth(true);
    setup("/tasks");
    expect(screen.getByRole("banner")).toHaveTextContent("Tasks");
    await userEvent.click(screen.getByRole("button", { name: "More" }));
    expect(screen.getByRole("dialog", { name: "More" })).toBeInTheDocument();
    resize(false);
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "More" })).toBeNull());
  });

  it("AC-172: Sign out from the desktop footer posts and lands on sign-in", async () => {
    stubWidth(false);
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        url === "/api/v1/pulse"
          ? Response.json({ inboxCount: 0, conditions: [] })
          : new Response(null, { status: 204 }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    const client = setup("/inbox");
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([u]) => u)).toContain("/api/v1/sign-out");
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it("AC-172: Sign out from More on phone lands on sign-in", async () => {
    stubWidth(true);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    setup("/inbox");
    await userEvent.click(screen.getByRole("button", { name: "More" }));
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "More" })).getByRole("button", {
        name: "Sign out",
      }),
    );
    expect(await screen.findByRole("heading", { name: "Sign in page" })).toBeInTheDocument();
  });
});

describe("AppShell connectivity ownership (AC-176)", () => {
  it("marks the shell active while mounted so a connectivity failure keeps the screen", () => {
    stubWidth(false);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}", { status: 200 })));
    setup("/inbox");
    expect(isShellActive()).toBe(true);
    resetConnectivity();
  });
});
