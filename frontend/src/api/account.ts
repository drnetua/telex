import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { ApiFailure, apiFetch } from "./client";

export interface Me {
  ownerId: string;
  email: string;
  linkedAccountCount: number;
}

export const meKey = ["me"] as const;

/** Refetches (not first loads) carry the background marker. */
function useBackgroundFlag(key: readonly string[]) {
  const client = useQueryClient();
  return () => (client.getQueryState(key)?.dataUpdateCount ?? 0) > 0;
}

/** The SPA's session state. */
export function useMe() {
  const background = useBackgroundFlag(meKey);
  return useQuery({
    queryKey: meKey,
    queryFn: () => apiFetch<Me>("/api/v1/me", { background: background() }),
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
  const background = useBackgroundFlag(passkeysKey);
  return useQuery({
    queryKey: passkeysKey,
    queryFn: () =>
      apiFetch<{ items: Passkey[] }>("/api/v1/passkeys", { background: background() }).then(
        (r) => r.items,
      ),
  });
}

export function useSessions() {
  const background = useBackgroundFlag(sessionsKey);
  return useQuery({
    queryKey: sessionsKey,
    queryFn: () =>
      apiFetch<{ items: SignInSession[] }>("/api/v1/sessions", {
        background: background(),
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
