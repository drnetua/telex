import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { meKey, type Me } from "../../api/account";
import { failureBus } from "../../app/queryClient";
import { THEME_KEY, startThemeRuntime } from "../../shell/theme";
import { ThemeSaveToast, ThemeSwitch } from "./ThemeSwitch";

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

/** The switch as the app mounts it: the failed-save toast comes from the always-mounted host in the layout. */
function setup(variant: "segmented" | "menu") {
  render(
    <QueryClientProvider client={client}>
      <ThemeSwitch variant={variant} />
      <ThemeSaveToast />
    </QueryClientProvider>,
  );
}

const stubDevice = (dark: boolean) =>
  vi.stubGlobal("matchMedia", () => ({
    matches: dark,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));

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

describe("ThemeSwitch keyboard (review C2, C6)", () => {
  it("AC-43: two segmented switches have distinct radio group names and arrows never cross into the other", async () => {
    render(
      <QueryClientProvider client={client}>
        <div data-testid="a">
          <ThemeSwitch variant="segmented" />
        </div>
        <div data-testid="b">
          <ThemeSwitch variant="segmented" />
        </div>
      </QueryClientProvider>,
    );
    const a = within(screen.getByTestId("a")).getAllByRole("radio");
    const b = within(screen.getByTestId("b")).getAllByRole("radio");
    expect(a[0]?.getAttribute("name")).not.toBe(b[0]?.getAttribute("name"));
    expect(new Set(a.map((r) => r.getAttribute("name"))).size).toBe(1);
    a[2]?.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(b).not.toContain(document.activeElement);
    expect(b.some((r) => (r as HTMLInputElement).checked && r !== b[0])).toBe(false);
    expect(a).toContain(document.activeElement);
  });

  it("the menu focuses the checked item on open and ArrowUp/ArrowDown/Home/End move focus", async () => {
    setup("menu");
    const trigger = screen.getByRole("button", { name: "Theme" });
    await userEvent.click(trigger);
    const items = screen.getAllByRole("menuitemradio");
    expect(items[0]).toHaveFocus();
    await userEvent.keyboard("{ArrowDown}");
    expect(items[1]).toHaveFocus();
    await userEvent.keyboard("{End}");
    expect(items[2]).toHaveFocus();
    await userEvent.keyboard("{ArrowDown}");
    expect(items[0]).toHaveFocus();
    await userEvent.keyboard("{ArrowUp}");
    expect(items[2]).toHaveFocus();
    await userEvent.keyboard("{Home}");
    expect(items[0]).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("the menu items are not tab stops, and Tab from an item closes the menu", async () => {
    setup("menu");
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    for (const item of screen.getAllByRole("menuitemradio"))
      expect(item).toHaveAttribute("tabindex", "-1");
    await userEvent.tab();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("the menu closes when focus moves outside it", async () => {
    render(
      <QueryClientProvider client={client}>
        <ThemeSwitch variant="menu" />
        <button type="button">Elsewhere</button>
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    act(() => screen.getByRole("button", { name: "Elsewhere" }).focus());
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("the menu opens with focus on a non-first checked item", async () => {
    client.setQueryData(meKey, { ...me, theme: "dark" });
    localStorage.setItem(THEME_KEY, "dark");
    setup("menu");
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    expect(screen.getByRole("menuitemradio", { name: "Dark" })).toHaveFocus();
  });
});

describe("one shared theme state (AC-179, AC-181, AC-182)", () => {
  it("AC-181: a theme changed in another tab moves the checked option with data-bs-theme", async () => {
    setup("segmented");
    const stop = startThemeRuntime();
    act(() => {
      localStorage.setItem(THEME_KEY, "dark");
      window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "dark" }));
    });
    expect(attr()).toBe("dark");
    expect(screen.getByRole("radio", { name: "Dark" })).toBeChecked();
    stop();
  });

  it("AC-181: a cross-device me refetch does not move the switch off the theme applied here", async () => {
    setup("segmented");
    // The query cache notifies observers on the next tick; let that land before asserting.
    await act(async () => {
      client.setQueryData<Me>(meKey, { ...me, theme: "dark" });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(attr()).toBe("light");
    expect(screen.getByRole("radio", { name: "Light" })).toBeChecked();
  });

  it("AC-182: with two switches, an earlier failed save never reverts the newer choice made in the other", async () => {
    stubDevice(true);
    render(
      <QueryClientProvider client={client}>
        <ThemeSwitch variant="segmented" />
        <ThemeSwitch variant="menu" />
        <ThemeSaveToast />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    expect(screen.getByRole("menuitemradio", { name: "Dark" })).toBeChecked();
    await userEvent.click(screen.getByRole("menuitemradio", { name: "System" }));
    expect(screen.getByRole("radio", { name: "System" })).toBeChecked();
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    expect(attr()).toBe("dark");
    expect(localStorage.getItem(THEME_KEY)).toBe("system");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "System" })).toBeChecked();
    await userEvent.click(screen.getByRole("button", { name: "Theme" }));
    expect(screen.getByRole("menuitemradio", { name: "System" })).toBeChecked();
  });

  it("AC-182: a save that fails after its switch unmounted still reverts and offers Try again", async () => {
    let hide: () => void = () => undefined;
    function Host() {
      const [shown, setShown] = useState(true);
      hide = () => setShown(false);
      return shown ? <ThemeSwitch variant="segmented" /> : null;
    }
    render(
      <QueryClientProvider client={client}>
        <Host />
        <ThemeSaveToast />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    act(() => hide());
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    await waitFor(() => expect(attr()).toBe("light"));
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
    expect(await screen.findByRole("alert")).toHaveTextContent("Your theme wasn't saved.");
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});

describe("a failed save reverts only its own choice (AC-181, AC-182)", () => {
  it("AC-182: a failure reverts to the theme applied here, not to a differing cached account theme", async () => {
    client.setQueryData<Me>(meKey, { ...me, theme: "dark" });
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "System" }));
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
    expect(attr()).toBe("light");
    expect(screen.getByRole("radio", { name: "Light" })).toBeChecked();
  });

  it("AC-181: a theme picked in another tab after the choice survives that choice's failed save", async () => {
    stubDevice(true);
    setup("segmented");
    const stop = startThemeRuntime();
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    act(() => {
      localStorage.setItem(THEME_KEY, "system");
      window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "system" }));
    });
    const writes = vi.spyOn(Storage.prototype, "setItem");
    await act(async () => patches[0]?.settle(new TypeError("offline")));
    expect(localStorage.getItem(THEME_KEY)).toBe("system");
    expect(writes).not.toHaveBeenCalledWith(THEME_KEY, expect.anything());
    expect(attr()).toBe("dark");
    expect(screen.getByRole("radio", { name: "System" })).toBeChecked();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    writes.mockRestore();
    stop();
  });

  it("AC-181: a server failure on a choice another tab replaced offers no retry that brings it back", async () => {
    const retries: Array<() => Promise<unknown>> = [];
    vi.spyOn(failureBus, "handler").mockImplementation((_failure, retry) => {
      retries.push(retry);
    });
    stubDevice(true);
    setup("segmented");
    const stop = startThemeRuntime();
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    act(() => {
      localStorage.setItem(THEME_KEY, "system");
      window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "system" }));
    });
    await act(async () => patches[0]?.settle(json(500, { code: "internal-error" })));
    // Try again on SCR-93, if the failure went there at all.
    await act(async () => {
      for (const retry of retries) void retry();
    });
    expect(localStorage.getItem(THEME_KEY)).toBe("system");
    expect(screen.getByRole("radio", { name: "System" })).toBeChecked();
    stop();
    vi.restoreAllMocks();
  });

  it("AC-173: a sign-in failure on a choice another tab replaced still goes to sign-in", async () => {
    const routes: Array<string | undefined> = [];
    vi.spyOn(failureBus, "handler").mockImplementation((failure) => {
      routes.push(failure.route);
    });
    stubDevice(true);
    setup("segmented");
    const stop = startThemeRuntime();
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    act(() => {
      localStorage.setItem(THEME_KEY, "system");
      window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "system" }));
    });
    await act(async () => patches[0]?.settle(json(401, { code: "unauthenticated" })));
    await waitFor(() => expect(routes).toEqual(["sign-in"]));
    expect(localStorage.getItem(THEME_KEY)).toBe("system");
    stop();
    vi.restoreAllMocks();
  });
});

describe("failed theme save routing (AC-173, AC-176, AC-182)", () => {
  afterEach(() => vi.restoreAllMocks());

  it.each([
    [401, "unauthenticated", "sign-in"],
    [401, "session-ended", "session-ended"],
    [500, "internal-error", "unavailable"],
  ])(
    "%i %s reverts, then goes to the failure routing (%s) with no toast",
    async (status, code, route) => {
      const seen: Array<{ route?: string; themeAtHandOff: string | null }> = [];
      vi.spyOn(failureBus, "handler").mockImplementation((failure) => {
        seen.push({ route: failure.route, themeAtHandOff: attr() });
      });
      setup("segmented");
      await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
      await act(async () => patches[0]?.settle(json(status, { code })));
      await waitFor(() => expect(seen).toHaveLength(1));
      expect(seen[0]).toEqual({ route, themeAtHandOff: "light" });
      expect(localStorage.getItem(THEME_KEY)).toBe("light");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    },
  );

  it.each([
    ["400", () => json(400, { code: "validation-failed" })],
    ["403", () => json(403, { code: "forbidden" })],
    ["no answer", () => new TypeError("offline")],
  ])("%s keeps the failed-save toast and stays out of the failure routing", async (_, answer) => {
    const handler = vi.spyOn(failureBus, "handler").mockImplementation(() => undefined);
    setup("segmented");
    await userEvent.click(screen.getByRole("radio", { name: "Dark" }));
    await act(async () => patches[0]?.settle(answer()));
    expect(await screen.findByRole("alert")).toHaveTextContent("Your theme wasn't saved.");
    expect(attr()).toBe("light");
    expect(handler).not.toHaveBeenCalled();
  });
});
