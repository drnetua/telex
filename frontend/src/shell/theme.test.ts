import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  THEME_KEY,
  applyTheme,
  currentChoice,
  rememberTheme,
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

  it("first-time device (nothing stored) adopts the account theme", () => {
    stubMatchMedia(false);
    applyTheme("system");
    renderHook(() => useAccountTheme(me("dark")));
    expect(attr()).toBe("dark");
    expect(currentChoice()).toBe("dark");
  });
});
