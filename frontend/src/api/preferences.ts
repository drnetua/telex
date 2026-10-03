import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useRef, useState } from "react";
import { applyTheme, currentChoice, rememberTheme, type ThemeChoice } from "../shell/theme";
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
 * Theme choice with apply-at-once, remember-on-this-device and revert-on-failure. Failures are handled here
 * (an error Toast with Try again) instead of the shared failure routing, so a refusal never leaves the screen.
 * The latest choice wins: a failed earlier save never reverts a newer choice.
 */
export function useChangeTheme() {
  const client = useQueryClient();
  const me = useQuery<Me>({ queryKey: meKey, enabled: false, staleTime: Infinity });
  const saved = me.data?.theme ?? currentChoice();
  const [pending, setPending] = useState<ThemeChoice | null>(null);
  const [failed, setFailed] = useState<ThemeChoice | null>(null);
  const latest = useRef(0);
  const lastSaved = useRef(0);

  const choose = useCallback(
    (choice: ThemeChoice) => {
      const seq = ++latest.current;
      setFailed(null);
      setPending(choice);
      applyTheme(choice);
      rememberTheme(choice);
      changePreferences({ theme: choice }).then(
        () => {
          if (seq > lastSaved.current) {
            lastSaved.current = seq;
            client.setQueryData<Me>(meKey, (old) => (old ? { ...old, theme: choice } : old));
          }
          if (seq === latest.current) setPending(null);
        },
        () => {
          if (seq !== latest.current) return;
          const previous = client.getQueryData<Me>(meKey)?.theme ?? saved;
          applyTheme(previous);
          rememberTheme(previous);
          setPending(null);
          setFailed(choice);
        },
      );
    },
    [client, saved],
  );

  return {
    /** What the control shows as checked: the choice in flight, else the saved one. */
    shown: pending ?? saved,
    choose,
    failed,
    retry: () => failed && choose(failed),
    dismiss: () => setFailed(null),
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

export type TimeZoneSaveResult = "saved" | "refused" | "failed";

/**
 * `changeMyPreferences` for the time zone: resolves with how it went (never rejects) so SCR-64 can draw
 * `tz-saved`, `tz-refused` or `tz-save-failed` itself. A saved zone replaces the cached `me` fields.
 */
export function useChangeTimeZone() {
  const client = useQueryClient();
  return useCallback(
    async (timeZone: string): Promise<TimeZoneSaveResult> => {
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
        return error instanceof ApiFailure && error.status === 400 ? "refused" : "failed";
      }
    },
    [client],
  );
}
