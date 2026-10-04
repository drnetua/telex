import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ApiFailure, apiFetch, isConnectivityStatus } from "../api/client";
import { connectivity } from "./connectivity";

export interface Pulse {
  inboxCount: number;
  conditions: unknown[];
}

const PULSE_INTERVAL_MS = 3000;
const PULSE_TIMEOUT_MS = 2000;
export const PULSE_KEY = ["pulse"] as const;

const visible = () => document.visibilityState === "visible";

export async function fetchPulse(): Promise<Pulse> {
  try {
    const pulse = await apiFetch<Pulse>("/api/v1/pulse", {
      background: true,
      timeoutMs: PULSE_TIMEOUT_MS,
    });
    connectivity.reportAnswered();
    return pulse;
  } catch (error) {
    if (error instanceof ApiFailure) {
      // A background pulse never takes over a screen (AC-122, AC-176): connectivity failures and 5xx are
      // "not responding" on the banner, whether or not the shell is mounted (SCR-02 has none), so the
      // rethrown failure carries no route. Only sign-in / session-ended keep theirs.
      if (isConnectivityStatus(error.status) || error.status >= 500) {
        connectivity.reportNoAnswer();
        throw new ApiFailure(error.status, error.code);
      }
      connectivity.reportAnswered();
      if (error.route !== "sign-in" && error.route !== "session-ended") {
        throw new ApiFailure(error.status, error.code);
      }
    }
    throw error;
  }
}

/** A sign-in or session-ended failure leaves the shell; the pulse stops instead of hammering a dead session. */
function stoppedByAuth(error: unknown): boolean {
  return (
    error instanceof ApiFailure && (error.route === "sign-in" || error.route === "session-ended")
  );
}

/** Background pulse: every 3 s while visible, never paused by the online manager (it detects recovery). */
export function usePulse() {
  const [isVisible, setVisible] = useState(visible);
  useEffect(() => {
    const onChange = () => setVisible(visible());
    document.addEventListener("visibilitychange", onChange);
    return () => document.removeEventListener("visibilitychange", onChange);
  }, []);
  // Becoming enabled again refetches at once (stale), so a returning tab never waits for the next tick.
  return useQuery({
    queryKey: PULSE_KEY,
    queryFn: fetchPulse,
    enabled: isVisible,
    networkMode: "always",
    refetchInterval: (query) => (stoppedByAuth(query.state.error) ? false : PULSE_INTERVAL_MS),
    staleTime: 0,
    retry: false,
  });
}
