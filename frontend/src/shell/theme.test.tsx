import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { render, renderHook } from "@testing-library/react";
import { useLayoutEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  THEME_KEY,
  applyTheme,
  currentChoice,
  rememberTheme,
  resetThemeMemory,
  startThemeRuntime,
  useAccountTheme,
  type ThemeChoice,
} from "./theme";

type Listener = () => void;

function stubMatchMedia(initialDark: boolean) {
  let dark = initialDark;
  const listeners = new Set<Listener>();
  vi.stubGlobal("matchMedia", (query: string) => ({
    get matches() {
      return query.includes("dark") && dark;
    },
    media: query,
    addEventListener: (_: string, l: Listener) => listeners.add(l),
    removeEventListener: (_: string, l: Listener) => listeners.delete(l),
  }));
  return {
    flip(next: boolean) {
      dark = next;
      listeners.forEach((l) => l());
    },
  };
}

const attr = () => document.documentElement.getAttribute("data-bs-theme");

beforeEach(() => {
  resetThemeMemory();
  localStorage.clear();
  document.documentElement.removeAttribute("data-bs-theme");
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("first-paint script in index.html (AC-181)", () => {
  const html = readFileSync(resolve(process.cwd(), "index.html"), "utf8");
  const script = /<script>([\s\S]*?)<\/script>/.exec(html)?.[1] ?? "";
  const run = () => new Function(script)();

  it("is an inline script in the head, before the app bundle", () => {
    expect(script).not.toBe("");
    expect(html.indexOf(script)).toBeLessThan(html.indexOf("/src/main.tsx"));
  });

  it.each([
    ["dark", false, "dark"],
    ["light", true, "light"],
    ["system", true, "dark"],
    ["system", false, "light"],
    [null, true, "dark"],
    [null, false, "light"],
    ["garbage", true, "dark"],
  ])("stored %s, device dark=%s -> %s", (stored, deviceDark, expected) => {
    stubMatchMedia(deviceDark);
    if (stored) localStorage.setItem(THEME_KEY, stored);
    run();
    expect(attr()).toBe(expected);
  });

  it("falls back to the device mode when localStorage throws", () => {
    stubMatchMedia(true);
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    run();
    expect(attr()).toBe("dark");
  });
});

describe("theme.ts", () => {
  it("applies and remembers a choice; System resolves through matchMedia", () => {
    stubMatchMedia(true);
    applyTheme("light");
    expect(attr()).toBe("light");
    applyTheme("system");
    expect(attr()).toBe("dark");
    rememberTheme("dark");
    expect(currentChoice()).toBe("dark");
  });

  it("currentChoice is system when nothing valid is stored or storage throws", () => {
    expect(currentChoice()).toBe("system");
    localStorage.setItem(THEME_KEY, "nope");
    expect(currentChoice()).toBe("system");
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(currentChoice()).toBe("system");
  });

  it("rememberTheme tolerates blocked storage", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(() => rememberTheme("dark")).not.toThrow();
  });

  it("keeps a choice that could not be written even when reads still work, until a write succeeds", () => {
    localStorage.setItem(THEME_KEY, "light");
    const write = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    rememberTheme("dark");
    expect(currentChoice()).toBe("dark");
    write.mockRestore();
    rememberTheme("system");
    expect(currentChoice()).toBe("system");
    localStorage.setItem(THEME_KEY, "light");
    expect(currentChoice()).toBe("light");
  });

  it("a choice saved in another tab replaces the one kept in memory", () => {
    stubMatchMedia(false);
    const stop = startThemeRuntime();
    vi.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new Error("quota");
    });
    rememberTheme("dark");
    localStorage.setItem(THEME_KEY, "light");
    window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "light" }));
    expect(currentChoice()).toBe("light");
    expect(attr()).toBe("light");
    stop();
  });

  it("follows the device mode live while on System, not otherwise (AC-180)", () => {
    const media = stubMatchMedia(false);
    const stop = startThemeRuntime();
    applyTheme("system");
    expect(attr()).toBe("light");
    rememberTheme("system");
    media.flip(true);
    expect(attr()).toBe("dark");
    media.flip(false);
    expect(attr()).toBe("light");
    rememberTheme("light");
    media.flip(true);
    expect(attr()).toBe("light");
    stop();
  });

  it("stops following after cleanup", () => {
    const media = stubMatchMedia(false);
    const stop = startThemeRuntime();
    applyTheme("system");
    stop();
    media.flip(true);
    expect(attr()).toBe("light");
  });

  it("follows another tab through the storage event", () => {
    stubMatchMedia(false);
    const stop = startThemeRuntime();
    localStorage.setItem(THEME_KEY, "dark");
    window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY, newValue: "dark" }));
    expect(attr()).toBe("dark");
    window.dispatchEvent(new StorageEvent("storage", { key: "other", newValue: "light" }));
    expect(attr()).toBe("dark");
    stop();
  });
});

describe("useAccountTheme (AC-181)", () => {
  const me = (theme: ThemeChoice) => ({ theme });

  it("switches exactly once when the account differs and remembers it", () => {
    stubMatchMedia(false);
    localStorage.setItem(THEME_KEY, "light");
    applyTheme("light");
    const set = vi.spyOn(document.documentElement, "setAttribute");
    const { rerender } = renderHook(({ m }) => useAccountTheme(m), {
      initialProps: { m: me("dark") as { theme: ThemeChoice } | undefined },
    });
    expect(attr()).toBe("dark");
    expect(currentChoice()).toBe("dark");
    rerender({ m: me("dark") });
    rerender({ m: me("dark") });
    expect(set.mock.calls.filter(([k]) => k === "data-bs-theme")).toHaveLength(1);
  });

  it("applies the account theme before the signed-in screen paints", () => {
    stubMatchMedia(false);
    applyTheme("system");
    const seen: (string | null)[] = [];
    function Account() {
      useAccountTheme(me("dark"));
      return null;
    }
    function Screen() {
      // Layout effects run in tree order before paint; this one sees what the first paint will show.
      useLayoutEffect(() => {
        seen.push(attr());
      }, []);
      return null;
    }
    render(
      <>
        <Account />
        <Screen />
      </>,
    );
    expect(seen).toEqual(["dark"]);
  });

  it("does not switch when stored equals account", () => {
    stubMatchMedia(false);
    localStorage.setItem(THEME_KEY, "dark");
    applyTheme("dark");
    const set = vi.spyOn(document.documentElement, "setAttribute");
    renderHook(() => useAccountTheme(me("dark")));
    expect(set).not.toHaveBeenCalled();
  });

  it("does nothing until me is known", () => {
    stubMatchMedia(false);
    applyTheme("light");
    renderHook(() => useAccountTheme(undefined));
    expect(attr()).toBe("light");
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });

  it.each([
    ["dark", false, true],
    ["light", true, false],
  ] as const)(
    "AC-180: with blocked storage, an account %s is not overridden by device mode changes",
    (theme, deviceDark, flipTo) => {
      const media = stubMatchMedia(deviceDark);
      vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
        throw new Error("blocked");
      });
      vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
        throw new Error("blocked");
      });
      // First paint on this device found nothing usable: System.
      rememberTheme("system");
      applyTheme("system");
      const stop = startThemeRuntime();
      renderHook(() => useAccountTheme(me(theme)));
      expect(attr()).toBe(theme);
      media.flip(flipTo);
      expect(attr()).toBe(theme);
      media.flip(!flipTo);
      expect(attr()).toBe(theme);
      stop();
    },
  );

  it("first-time device (nothing stored) adopts the account theme", () => {
    stubMatchMedia(false);
    applyTheme("system");
    renderHook(() => useAccountTheme(me("dark")));
    expect(attr()).toBe("dark");
    expect(currentChoice()).toBe("dark");
  });
});
