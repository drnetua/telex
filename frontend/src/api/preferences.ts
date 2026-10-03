import { type QueryClient, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { failureBus } from "../app/queryClient";
import {
  applyTheme,
  currentChoice,
  rememberTheme,
  type ThemeChoice,
  useAppliedTheme,
} from "../shell/theme";
import { meKey, type Me } from "./account";
import { ApiFailure, apiFetch } from "./client";

/** `changeMyPreferences`: resolves with the saved preferences, rejects with an `ApiFailure`. */
export function changePreferences(change: { theme: ThemeChoice }): Promise<{ theme: ThemeChoice }> {
  return apiFetch<{ theme: ThemeChoice }>("/api/v1/me/preferences", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(change),
  });
}

export interface SavedPreferences {
  theme: ThemeChoice;
  timeZone: string | null;
  timeZoneIsFallback: boolean;
}

/**
 * `saveDetectedTimeZone`: fire-and-forget background write; on 200 the returned preferences replace the cached
 * `me` fields. A failed save is left to the connectivity banner, and the next open tries again.
 */
export function useSaveDetectedTimeZoneRequest() {
  const client = useQueryClient();
  return useCallback(
    (timeZone: string | null) => {
      apiFetch<SavedPreferences>("/api/v1/me/preferences/detected-time-zone", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ timeZone }),
        background: true,
      }).then(
        (saved) =>
          client.setQueryData<Me>(meKey, (old) =>
            old
              ? { ...old, timeZone: saved.timeZone, timeZoneIsFallback: saved.timeZoneIsFallback }
              : old,
          ),
        () => undefined,
      );
    },
    [client],
  );
}

/**
 * A failed preference save that the shared failure routing owns: sign-in, session ended, or teleX answering
 * with a server failure (SCR-93). A refusal (400), a 403 and no answer stay on the screen as a toast.
 */
function goesToFailureRouting(error: unknown): error is ApiFailure {
  return (
    error instanceof ApiFailure &&
    (error.route === "sign-in" ||
      error.route === "session-ended" ||
      (error.route === "unavailable" && error.status >= 500))
  );
}

/** The one theme save state every ThemeSwitch and the failed-save toast share (per query client). */
interface ThemeSave {
  /** Bumped by every choice; only the latest may revert or clear `pending`. */
  latest: number;
  /** The newest choice the account confirmed. */
  lastSaved: number;
  /** What a failed save returns to: the theme applied here before the choices in flight, or the newest saved. */
  settled: ThemeChoice;
  pending: ThemeChoice | null;
  failed: ThemeChoice | null;
}

const themeSaveKey = ["theme-save"] as const;

function readThemeSave(client: QueryClient): ThemeSave {
  return (
    client.getQueryData<ThemeSave>(themeSaveKey) ?? {
      latest: 0,
      lastSaved: 0,
      settled: currentChoice(),
      pending: null,
      failed: null,
    }
  );
}

function writeThemeSave(client: QueryClient, change: Partial<ThemeSave>): void {
  client.setQueryData<ThemeSave>(themeSaveKey, { ...readThemeSave(client), ...change });
}

/** Applies and remembers at once, then saves; the latest choice wins, so an earlier failure never reverts it. */
function chooseTheme(client: QueryClient, choice: ThemeChoice): Promise<void> {
  const before = readThemeSave(client);
  const seq = before.latest + 1;
  writeThemeSave(client, {
    latest: seq,
    settled: before.pending === null ? currentChoice() : before.settled,
    pending: choice,
    failed: null,
  });
  applyTheme(choice);
  rememberTheme(choice);
  return changePreferences({ theme: choice }).then(
    () => {
      const now = readThemeSave(client);
      if (seq > now.lastSaved) {
        writeThemeSave(client, { lastSaved: seq, settled: choice });
        client.setQueryData<Me>(meKey, (old) => (old ? { ...old, theme: choice } : old));
      }
      if (seq === now.latest) writeThemeSave(client, { pending: null });
    },
    (error: unknown) => {
      const now = readThemeSave(client);
      if (seq !== now.latest) return;
      applyTheme(now.settled);
      rememberTheme(now.settled);
      const routed = goesToFailureRouting(error);
      writeThemeSave(client, { pending: null, failed: routed ? null : choice });
      if (routed) failureBus.handler(error, () => chooseTheme(client, choice));
    },
  );
}

/**
 * Theme choice with apply-at-once, remember-on-this-device and revert-on-failure. The checked option is the
 * theme applied on this device, never the cached account theme. A failure the shared routing owns (sign-in,
 * session ended, a server failure) goes there after the revert; any other failure is offered back through the
 * error Toast with Try again, raised by `ThemeSaveToast` in the always-mounted layout.
 */
export function useChangeTheme() {
  const client = useQueryClient();
  const shown = useAppliedTheme();
  const save = useQuery<ThemeSave>({
    queryKey: themeSaveKey,
    enabled: false,
    staleTime: Infinity,
    gcTime: Infinity,
    initialData: () => readThemeSave(client),
  });
  const failed = save.data.failed;

  return {
    /** What the control shows as checked: the theme applied on this device. */
    shown,
    choose: useCallback((choice: ThemeChoice) => void chooseTheme(client, choice), [client]),
    failed,
    retry: () => {
      if (failed) void chooseTheme(client, failed);
    },
    dismiss: () => writeThemeSave(client, { failed: null }),
  };
}

/** `listTimeZones`: the whole known list, fetched only while the picker is open. */
export function useListTimeZones(enabled: boolean) {
  return useQuery({
    queryKey: ["time-zones"],
    enabled,
    staleTime: Infinity,
    retry: false,
    queryFn: () => apiFetch<{ items: string[] }>("/api/v1/time-zones").then((r) => r.items),
  });
}

export type TimeZoneSaveResult = "saved" | "refused" | "failed" | "routed";

/**
 * `changeMyPreferences` for the time zone: resolves with how it went (never rejects) so SCR-64 can draw
 * `tz-saved`, `tz-refused` or `tz-save-failed` itself. A saved zone replaces the cached `me` fields. A failure
 * the shared routing owns goes there instead and resolves `routed`.
 */
export function useChangeTimeZone() {
  const client = useQueryClient();
  return useCallback(
    (timeZone: string): Promise<TimeZoneSaveResult> => {
      const save = async (): Promise<TimeZoneSaveResult> => {
        try {
          const saved = await apiFetch<SavedPreferences>("/api/v1/me/preferences", {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ timeZone }),
          });
          client.setQueryData<Me>(meKey, (old) =>
            old
              ? { ...old, timeZone: saved.timeZone, timeZoneIsFallback: saved.timeZoneIsFallback }
              : old,
          );
          return "saved";
        } catch (error) {
          if (goesToFailureRouting(error)) {
            failureBus.handler(error, save);
            return "routed";
          }
          return error instanceof ApiFailure && error.status === 400 ? "refused" : "failed";
        }
      };
      return save();
    },
    [client],
  );
}
