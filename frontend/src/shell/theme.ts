import { useLayoutEffect, useRef, useSyncExternalStore } from "react";

export type ThemeChoice = "light" | "dark" | "system";

/** localStorage key holding the theme last used on this device. */
export const THEME_KEY = "telex.theme";

const DARK_QUERY = "(prefers-color-scheme: dark)";

const isChoice = (value: unknown): value is ThemeChoice =>
  value === "light" || value === "dark" || value === "system";

const deviceIsDark = () => window.matchMedia(DARK_QUERY).matches;

/** The choice kept while storage can't be written (blocked or full), so it lasts as long as this page does. */
let blockedStorageChoice: ThemeChoice | null = null;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((listener) => listener());
const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/**
 * The theme last used on this device; System when nothing valid is stored. When the last choice couldn't be
 * written (blocked or full storage) it is the choice kept in memory for this page.
 */
export function currentChoice(): ThemeChoice {
  if (blockedStorageChoice !== null) return blockedStorageChoice;
  try {
    const stored = localStorage.getItem(THEME_KEY);
    return isChoice(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

export function rememberTheme(choice: ThemeChoice): void {
  try {
    localStorage.setItem(THEME_KEY, choice);
    blockedStorageChoice = null;
  } catch {
    // Blocked or full storage: keep it for this page; the account theme still applies on every load.
    blockedStorageChoice = choice;
  }
  notify();
}

/** Test helper: forget the choice kept in memory for this page. */
export function resetThemeMemory(): void {
  blockedStorageChoice = null;
}

/** The choice applied on this device, shared by every theme control and kept current across tabs. */
export function useAppliedTheme(): ThemeChoice {
  return useSyncExternalStore(subscribe, currentChoice);
}

/** Sets `data-bs-theme`, resolving System through the device's mode. */
export function applyTheme(choice: ThemeChoice): void {
  const resolved = choice === "system" ? (deviceIsDark() ? "dark" : "light") : choice;
  document.documentElement.setAttribute("data-bs-theme", resolved);
}

/** Follows device mode changes while on System and theme changes made in other tabs. Returns its cleanup. */
export function startThemeRuntime(): () => void {
  const media = window.matchMedia(DARK_QUERY);
  const onDeviceChange = () => {
    if (currentChoice() === "system") applyTheme("system");
  };
  const onStorage = (event: StorageEvent) => {
    if (event.key !== THEME_KEY) return;
    // Another tab saved a newer choice; it replaces the one this page kept in memory.
    blockedStorageChoice = null;
    applyTheme(currentChoice());
    notify();
  };
  media.addEventListener("change", onDeviceChange);
  window.addEventListener("storage", onStorage);
  return () => {
    media.removeEventListener("change", onDeviceChange);
    window.removeEventListener("storage", onStorage);
  };
}

/**
 * Switches once to the account's theme when it differs from the one applied at first paint. A layout effect, so
 * the first signed-in paint already shows the account's theme (AC-181).
 */
export function useAccountTheme(me: { theme: ThemeChoice } | undefined): void {
  const done = useRef(false);
  const theme = me?.theme;
  useLayoutEffect(() => {
    if (!theme || done.current) return;
    done.current = true;
    if (theme !== currentChoice()) {
      applyTheme(theme);
      rememberTheme(theme);
    }
  }, [theme]);
}
