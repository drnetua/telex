import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { readFileSync, statSync } from "node:fs";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "./AppRoutes";
import { AppLayout, AuthLayout } from "./layouts";

describe("layouts (AC-83, AC-100)", () => {
  it("C2: the auth layout shows the 96 px logo asset", () => {
    render(
      <MemoryRouter initialEntries={["/x"]}>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/x" element={<p>content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    const logo = screen.getByRole("img", { name: "teleX" });
    expect(logo).toHaveAttribute("src", expect.stringContaining("telex-logo"));
    expect(logo).toHaveAttribute("width", "96");
    expect(logo).toHaveAttribute("height", "96");
  });

  it("R7: the 96 px slot uses a derived 192 px logo well under 50 KB; the canonical file is untouched", () => {
    render(
      <MemoryRouter initialEntries={["/x"]}>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/x" element={<p>content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole("img", { name: "teleX" })).toHaveAttribute(
      "src",
      expect.stringContaining("telex-logo-192"),
    );
    const derived = "src/assets/telex-logo-192.png";
    expect(statSync(derived).size).toBeLessThan(50_000);
    expect(readFileSync(derived).readUInt32BE(16)).toBe(192);
    const canonical = "src/assets/telex-logo.png";
    expect(readFileSync(canonical).readUInt32BE(16)).toBe(1254);
  });

  it("starting: AppLayout shows no navigation while getMe is in flight, then the shell", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ ownerId: "o1", email: "me@example.com", theme: "system" }),
            { status: 200, headers: { "Content-Type": "application/json" } },
          ),
        ),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={["/inbox"]}>
          <Routes>
            <Route element={<AppLayout />}>
              <Route path="/inbox" element={<p>content</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.queryByRole("navigation")).toBeNull();
    expect(await screen.findByRole("navigation", { name: "Main" })).toBeInTheDocument();
    expect(screen.getByText("content")).toBeInTheDocument();
    vi.unstubAllGlobals();
  });
});

describe("SCR-02 onboarding layout (AC-01, S10)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders /connect-telegram in the onboarding card: logo, no app shell, banner above the card", async () => {
    const lost = {
      id: "a1",
      displayName: "Anna",
      phone: { countryCode: "380", lastDigits: "42" },
      state: "session_lost",
      chatSync: { chatsSynced: 0, chatsTotal: null, completedAt: null },
      linkedAt: "x",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((input: RequestInfo | URL) =>
        Promise.resolve(
          String(input).includes("linking-attempt")
            ? new Response("{}", {
                status: 404,
                headers: { "Content-Type": "application/problem+json" },
              })
            : new Response(JSON.stringify({ items: [lost] }), {
                status: 200,
                headers: { "Content-Type": "application/json" },
              }),
        ),
      ),
    );
    const { container } = render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
      >
        <MemoryRouter initialEntries={["/connect-telegram"]}>
          <AppRoutes />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByRole("img", { name: "teleX" })).toHaveAttribute("width", "96");
    expect(container.querySelector("header.navbar")).toBeNull();
    expect(screen.queryByRole("navigation", { name: "Main" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Sign out" })).toBeNull();
    const banner = await screen.findByText(/Anna.s Telegram is disconnected/);
    const card = container.querySelector(".card");
    expect(card).not.toBeNull();
    expect(container.querySelectorAll(".card")).toHaveLength(1);
    expect(banner.compareDocumentPosition(card!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
