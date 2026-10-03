import { onlineManager } from "@tanstack/react-query";

export type ConnectivityState = "online" | "offline" | "not-responding";

type Listener = () => void;

let state: ConnectivityState = "online";
let shellActive = false;
const listeners = new Set<Listener>();

function set(next: ConnectivityState) {
  if (next === state) return;
  state = next;
  onlineManager.setOnline(next === "online");
  listeners.forEach((l) => l());
}

/** One connectivity state shared by the fetch client, the pulse and the Status Banner. */
export const connectivity = {
  get: (): ConnectivityState => state,
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  /** No answer, a network error or a 502/503/504: teleX is not reachable. */
  reportNoAnswer() {
    if (state !== "offline") set("not-responding");
  },
  /** teleX answered: the first answer restores online. */
  reportAnswered() {
    set("online");
  },
};

/** The shell sets this while mounted; connectivity failures keep the screen only then. */
export function setShellActive(active: boolean) {
  shellActive = active;
}

export function isShellActive(): boolean {
  return shellActive;
}

/** Fires one pulse now ("Try again") and says whether teleX is still unreachable. */
export async function retryNow(): Promise<{ stillDown: boolean }> {
  const { fetchPulse } = await import("./pulse");
  try {
    await fetchPulse();
  } catch {
    // classified by fetchPulse into the connectivity state
  }
  return { stillDown: state !== "online" };
}

/** Test helper: back to a fresh online state. */
export function resetConnectivity() {
  state = "online";
  shellActive = false;
  onlineManager.setOnline(true);
  listeners.clear();
}

if (typeof window !== "undefined") {
  window.addEventListener("offline", () => set("offline"));
  window.addEventListener("online", () => {
    if (state === "offline") set("online");
  });
}
