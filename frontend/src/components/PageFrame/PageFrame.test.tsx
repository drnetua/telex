import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PageFrame } from "./PageFrame";

const json = (body: unknown) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });

afterEach(() => vi.unstubAllGlobals());

function setup(items: unknown[]) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ items })));
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={["/inbox"]}>
        <Routes>
          <Route path="/inbox" element={<PageFrame>content</PageFrame>} />
          <Route path="/accounts" element={<h1>Accounts page</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("PageFrame", () => {
  it("has an Accounts button before Profile that opens Accounts", async () => {
    setup([]);
    const buttons = screen.getAllByRole("button");
    expect(buttons[0]).toHaveAccessibleName("Accounts");
    expect(buttons[1]).toHaveAccessibleName("Profile and security");
    await userEvent.click(buttons[0]!);
    expect(await screen.findByRole("heading", { name: "Accounts page" })).toBeInTheDocument();
  });

  it("AC-122: shows the disconnected banner under the header", async () => {
    setup([
      {
        id: "a1",
        displayName: "Anna",
        phone: { countryCode: "380", lastDigits: "42" },
        state: "session_lost",
        chatSync: { chatsSynced: 0, chatsTotal: null, completedAt: null },
        linkedAt: "x",
      },
    ]);
    expect(await screen.findByRole("status")).toHaveTextContent("Anna's Telegram is disconnected");
  });
});
