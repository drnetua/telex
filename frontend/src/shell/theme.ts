import { useEffect, useRef } from "react";

export type ThemeChoice = "light" | "dark" | "system";

/** localStorage key holding the theme last used on this device. */
export const THEME_KEY = "telex.theme";

const DARK_QUERY = "(prefers-color-scheme: dark)";

const isChoice = (value: unknown): value is ThemeChoice =>
  value === "light" || value === "dark" || value === "system";

const deviceIsDark = () => window.matchMedia(DARK_QUERY).matches;

/** The theme last used on this device; System when nothing valid is stored or storage is blocked. */
export function currentChoice(): ThemeChoice {
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
  } catch {
    // Blocked storage: the account theme still applies on every load.
  }
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
    if (event.key === THEME_KEY) applyTheme(currentChoice());
  };
  media.addEventListener("change", onDeviceChange);
  window.addEventListener("storage", onStorage);
  return () => {
    media.removeEventListener("change", onDeviceChange);
    window.removeEventListener("storage", onStorage);
  };
}

/** Switches once to the account's theme when it differs from the one applied at first paint. */
export function useAccountTheme(me: { theme: ThemeChoice } | undefined): void {
  const done = useRef(false);
  const theme = me?.theme;
  useEffect(() => {
    if (!theme || done.current) return;
    done.current = true;
    if (theme !== currentChoice()) {
      applyTheme(theme);
      rememberTheme(theme);
    }
  }, [theme]);
}
