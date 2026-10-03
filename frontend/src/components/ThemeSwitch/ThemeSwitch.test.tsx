import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { meKey, type Me } from "../../api/account";
import { THEME_KEY } from "../../shell/theme";
import { ThemeSwitch } from "./ThemeSwitch";

const me: Me = {
  ownerId: "o1",
  email: "me@example.com",
  linkedAccountCount: 0,
  theme: "light",
  timeZone: null,
  timeZoneIsFallback: false,
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const attr = () => document.documentElement.getAttribute("data-bs-theme");

type Resolver = (r: Response | Error) => void;
let client: QueryClient;
let patches: Array<{ body: unknown; settle: Resolver }>;

beforeEach(() => {
  localStorage.setItem(THEME_KEY, "light");
  document.documentElement.setAttribute("data-bs-theme", "light");
  vi.stubGlobal("matchMedia", () => ({
    matches: false,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
  patches = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(
      (_url: string, init?: RequestInit) =>
        new Promise<Response>((resolve, reject) => {
          patches.push({
            body: JSON.parse(String(init?.body)),
            settle: (r) => (r instanceof Error ? reject(r) : resolve(r)),
          });
        }),
    ),
  );
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(meKey, me);
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

function setup(variant: "segmented" | "menu") {
  render(
    <QueryClientProvider client={client}>
      <ThemeSwitch variant={variant} />
    </QueryClientProvider>,
  );
}

describe("ThemeSwitch segmented", () => {
  it("AC-179: applies and remembers Dark before the save resolves, then keeps it when saved", async () => {
    setup("segmented");
    expect(screen.getByRole("radio", { name: "Light" })).toBeChecked();
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    expect(attr()).toBe("dark");
    expect(localStorage.getItem(THEME_KEY)).toBe("dark");
    expect(patches).toHaveLength(1);
    expect(patches[0]?.body).toEqual({ theme: "dark" });
    await act(async () => patches[0]?.settle(json(200, { theme: "dark" })));
    await waitFor(() => expect(client.getQueryData<Me>(meKey)?.theme).toBe("dark"));
    expect(attr()).toBe("dark");
    expect(screen.getByRole("radio", { name: "Dark" })).toBeChecked();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("AC-182: reverts theme and device memory on 400 and shows an error toast", async () => {
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await act(async () =>
      patches[0]?.settle(
        json(400, {
          code: "validation-failed",
          errors: [{ field: "theme", code: "unknown-theme" }],
        }),
      ),
    );
    await waitFor(() => expect(attr()).toBe("light"));
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
    expect(await screen.findByRole("alert")).toHaveTextContent("Your theme wasn't saved.");
    expect(screen.getByRole("radio", { name: "Light" })).toBeChecked();
  });

  it("AC-182: reverts when there is no answer", async () => {
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    await waitFor(() => expect(attr()).toBe("light"));
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
    expect(await screen.findByRole("alert")).toHaveTextContent("Your theme wasn't saved.");
  });

  it("Try again re-applies and re-sends the failed choice", async () => {
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await act(async () => patches[0]?.settle(json(403, { code: "forbidden" })));
    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));
    expect(attr()).toBe("dark");
    expect(localStorage.getItem(THEME_KEY)).toBe("dark");
    expect(patches).toHaveLength(2);
    expect(patches[1]?.body).toEqual({ theme: "dark" });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await act(async () => patches[1]?.settle(json(200, { theme: "dark" })));
    await waitFor(() => expect(client.getQueryData<Me>(meKey)?.theme).toBe("dark"));
  });

  it("the latest choice wins over an earlier response", async () => {
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await userEvent.click(screen.getByRole("radio", { name: "System" }));
    expect(patches).toHaveLength(2);
    await act(async () => patches[0]?.settle(json(500, { code: "internal-error" })));
    expect(attr()).toBe("light");
    expect(localStorage.getItem(THEME_KEY)).toBe("system");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await act(async () => patches[1]?.settle(json(200, { theme: "system" })));
    await waitFor(() => expect(client.getQueryData<Me>(meKey)?.theme).toBe("system"));
  });
});

describe("ThemeSwitch menu", () => {
  it("opens a menu of radio items with the saved one checked, Escape closes and returns focus", async () => {
    setup("menu");
    const trigger = screen.getByRole("button", { name: "Theme" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(screen.getAllByRole("menuitemradio")).toHaveLength(3);
    expect(screen.getByRole("menuitemradio", { name: "Light" })).toBeChecked();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("AC-179: choosing Dark applies at once and closes the menu", async () => {
    setup("menu");
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    await userEvent.click(screen.getByRole("menuitemradio", { name: "Dark" }));
    expect(attr()).toBe("dark");
    expect(patches[0]?.body).toEqual({ theme: "dark" });
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("AC-182: a failed save reverts and offers Try again", async () => {
    setup("menu");
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    await userEvent.click(screen.getByRole("menuitemradio", { name: "Dark" }));
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    await waitFor(() => expect(attr()).toBe("light"));
    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));
    expect(attr()).toBe("dark");
    expect(patches).toHaveLength(2);
  });
});
