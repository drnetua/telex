import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { markBackgroundTick } from "./account";
import { linkedAccountsKey } from "./linkedAccounts";

export const LIVE_UPDATES_URL = "/api/v1/live-updates";

/** Hint name (the SSE `data:`) to the query it invalidates (ADR-0005). E04 and E06 add entries. */
const hintKeys: Record<string, readonly unknown[]> = {
  "linked-accounts": linkedAccountsKey,
};

type EventSourceFactory = (url: string) => EventSource;

/**
 * Opens the tab's single live stream. A hint refetches its query in the background; after a drop and
 * reconnect everything the SPA shows is refetched, so a missed hint never leaves stale state.
 * Returns a closer.
 */
export function openLiveUpdates(
  client: QueryClient,
  create: EventSourceFactory = (url) => new EventSource(url),
): () => void {
  const source = create(LIVE_UPDATES_URL);
  let dropped = false;

  source.addEventListener("hint", (event) => {
    const key = hintKeys[(event as MessageEvent<string>).data.trim()];
    if (!key) return; // unknown hint name: ignored
    markBackgroundTick();
    void client.invalidateQueries({ queryKey: key });
  });
  source.addEventListener("error", () => {
    dropped = true;
  });
  source.addEventListener("open", () => {
    if (!dropped) return;
    dropped = false;
    markBackgroundTick();
    void client.invalidateQueries();
  });

  return () => source.close();
}

/** Mount once, in App. */
export function useLiveUpdates(): void {
  const client = useQueryClient();
  useEffect(() => {
    if (typeof EventSource === "undefined") return undefined;
    return openLiveUpdates(client);
  }, [client]);
}
