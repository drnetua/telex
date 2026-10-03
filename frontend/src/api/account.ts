import {
  focusManager,
  onlineManager,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useNavigate } from "react-router";
import type { ThemeChoice } from "../shell/theme";
import { ApiFailure, apiFetch } from "./client";

export interface Me {
  ownerId: string;
  email: string;
  linkedAccountCount: number;
  theme: ThemeChoice;
  timeZone: string | null;
  timeZoneIsFallback: boolean;
}

export const meKey = ["me"] as const;

let backgroundTrigger = false;

/** Window-focus and reconnect refetches start synchronously inside the event; the flag lives for that tick. */
function markBackgroundTick() {
  backgroundTrigger = true;
  setTimeout(() => (backgroundTrigger = false), 0);
}
focusManager.subscribe((focused) => focused && markBackgroundTick());
onlineManager.subscribe((online) => online && markBackgroundTick());

/** Only focus and reconnect refetches carry the background marker; page opens, mutations and first loads count as activity. */
export const isBackground = () => backgroundTrigger;

/** The SPA's session state. */
export function useMe() {
  return useQuery({
    queryKey: meKey,
    queryFn: () => apiFetch<Me>("/api/v1/me", { background: isBackground() }),
  });
}

export function useSignOut() {
  const client = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/sign-out", { method: "POST" }),
    onSuccess: () => {
      client.clear();
      void navigate("/sign-in", { replace: true });
    },
  });
}

export interface Passkey {
  id: string;
  label: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export type DeviceType = "phone" | "tablet" | "computer" | "unknown";

export interface SignInSession {
  id: string;
  userAgentLabel: string;
  deviceType: DeviceType;
  startedAt: string;
  lastActivityAt: string;
  current: boolean;
}

export const passkeysKey = ["passkeys"] as const;
export const sessionsKey = ["sessions"] as const;

export function usePasskeys() {
  return useQuery({
    queryKey: passkeysKey,
    queryFn: () =>
      apiFetch<{ items: Passkey[] }>("/api/v1/passkeys", { background: isBackground() }).then(
        (r) => r.items,
      ),
  });
}

export function useSessions() {
  return useQuery({
    queryKey: sessionsKey,
    queryFn: () =>
      apiFetch<{ items: SignInSession[] }>("/api/v1/sessions", {
        background: isBackground(),
      }).then((r) => r.items),
  });
}

/** A 404 means it is already gone, which is the outcome the Owner asked for. */
async function deleteTolerant(url: string): Promise<void> {
  try {
    await apiFetch<void>(url, { method: "DELETE" });
  } catch (error) {
    if (!(error instanceof ApiFailure && error.status === 404)) throw error;
  }
}

export function useRemovePasskey() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTolerant(`/api/v1/passkeys/${encodeURIComponent(id)}`),
    onSettled: () => client.invalidateQueries({ queryKey: passkeysKey }),
  });
}

export function useEndSession() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteTolerant(`/api/v1/sessions/${encodeURIComponent(id)}`),
    onSettled: () => client.invalidateQueries({ queryKey: sessionsKey }),
  });
}

export function useEndOtherSessions() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/sessions/end-others", { method: "POST" }),
    onSettled: () => client.invalidateQueries({ queryKey: sessionsKey }),
  });
}
