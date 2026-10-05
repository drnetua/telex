import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useEffect, useSyncExternalStore } from "react";
import { markBackgroundTick } from "./account";
import { linkedAccountsKey } from "./linkedAccounts";

export const LIVE_UPDATES_URL = "/api/v1/live-updates";

/** Hint name (the SSE `data:`) to the query it invalidates (ADR-0005). E04 and E06 add entries. */
const hintKeys: Record<string, readonly unknown[]> = {
  "linked-accounts": linkedAccountsKey,
};

type EventSourceFactory = (url: string) => EventSource;

const READY_STATE_CLOSED = 2; // EventSource.CLOSED, spelled out so it also resolves where the global is absent
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_CAP_MS = 30_000;

/**
 * Opens the tab's single live stream. A hint refetches its query in the background; after a drop and
 * reconnect everything the SPA shows is refetched, so a missed hint never leaves stale state. The browser retries
 * network errors itself, but gives up on any non-event-stream answer (a proxy 502 during a restart, a 401), so a
 * closed source is recreated here with capped exponential backoff. Returns a closer.
 */
export function openLiveUpdates(
  client: QueryClient,
  create: EventSourceFactory = (url) => new EventSource(url),
): () => void {
  let source: EventSource | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;
  let dropped = false;
  let delay = RECONNECT_BASE_MS;

  const connect = () => {
    const current = create(LIVE_UPDATES_URL);
    source = current;
    current.addEventListener("hint", (event) => {
      const key = hintKeys[(event as MessageEvent<string>).data.trim()];
      if (!key) return; // unknown hint name: ignored
      markBackgroundTick();
      void client.invalidateQueries({ queryKey: key });
    });
    current.addEventListener("error", () => {
      dropped = true;
      if (stopped || current.readyState !== READY_STATE_CLOSED) return; // still connecting: the browser retries
      current.close();
      timer = setTimeout(connect, delay);
      delay = Math.min(delay * 2, RECONNECT_CAP_MS);
    });
    current.addEventListener("open", () => {
      delay = RECONNECT_BASE_MS;
      if (!dropped) return;
      dropped = false;
      markBackgroundTick();
      void client.invalidateQueries();
    });
  };
  connect();

  return () => {
    stopped = true;
    clearTimeout(timer);
    source?.close();
  };
}

/**
 * Mount once, in App. The stream needs a Sign-in Session: a stream opened while signed out is answered 401 and an
 * EventSource never retries that, so it opens once a signed-in screen has loaded the Owner's accounts and closes when the Owner signs out.
 */
export function useLiveUpdates(): void {
  const client = useQueryClient();
  const signedIn = useSyncExternalStore(
    (notify) => client.getQueryCache().subscribe(notify),
    () => client.getQueryData(linkedAccountsKey) !== undefined,
  );
  useEffect(() => {
    if (!signedIn || typeof EventSource === "undefined") return undefined;
    return openLiveUpdates(client);
  }, [client, signedIn]);
}
