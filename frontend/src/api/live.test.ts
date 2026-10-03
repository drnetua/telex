import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, act } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isBackground } from "./account";
import { linkedAccountsKey } from "./linkedAccounts";
import { openLiveUpdates, useLiveUpdates } from "./live";

class FakeSource {
  closed = false;
  readyState = 0;
  private listeners = new Map<string, ((e: Event) => void)[]>();
  constructor(readonly url: string) {}
  addEventListener(type: string, fn: (e: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }
  close() {
    this.closed = true;
    this.readyState = 2;
  }
  emit(type: string, data?: string) {
    const event = data === undefined ? new Event(type) : new MessageEvent(type, { data });
    this.listeners.get(type)?.forEach((fn) => fn(event));
  }
}

function setup() {
  const client = new QueryClient();
  const spy = vi.spyOn(client, "invalidateQueries");
  let source!: FakeSource;
  const close = openLiveUpdates(client, (url) => (source = new FakeSource(url)) as never);
  return { client, spy, source, close };
}

describe("openLiveUpdates (AC-116, AC-121, AC-122)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("opens the one stream at the live-updates path", () => {
    expect(setup().source.url).toBe("/api/v1/live-updates");
  });

  it("turns a linked-accounts hint into a background invalidation of that query", () => {
    const { spy, source } = setup();
    spy.mockImplementation(() => {
      expect(isBackground()).toBe(true);
      return Promise.resolve();
    });
    source.emit("hint", "linked-accounts");
    expect(spy).toHaveBeenCalledWith({ queryKey: ["linked-accounts"] });
  });

  it("ignores unknown hint names", () => {
    const { spy, source } = setup();
    source.emit("hint", "something-else");
    expect(spy).not.toHaveBeenCalled();
  });

  it("invalidates everything when the stream reopens after an error", () => {
    const { spy, source } = setup();
    source.emit("open");
    expect(spy).not.toHaveBeenCalled();
    source.emit("error");
    source.emit("open");
    expect(spy).toHaveBeenCalledWith();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it("closes the stream on cleanup", () => {
    const { source, close } = setup();
    close();
    expect(source.closed).toBe(true);
  });
});

describe("openLiveUpdates reconnect (AC-116, AC-117, AC-122)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setupMany() {
    const client = new QueryClient();
    const spy = vi.spyOn(client, "invalidateQueries");
    const sources: FakeSource[] = [];
    const close = openLiveUpdates(client, (url) => {
      const s = new FakeSource(url);
      sources.push(s);
      return s as never;
    });
    return { spy, sources, close };
  }

  /** The browser gave up: readyState CLOSED, then an error event. */
  const giveUp = (s: FakeSource) => {
    s.readyState = 2;
    s.emit("error");
  };

  it("leaves a still-connecting source to the browser's own retry", () => {
    const { sources } = setupMany();
    sources[0]!.readyState = 0;
    sources[0]!.emit("error");
    vi.advanceTimersByTime(120_000);
    expect(sources).toHaveLength(1);
  });

  it("reopens a stream the browser has closed, after the backoff, and refetches everything", () => {
    const { sources, spy } = setupMany();
    giveUp(sources[0]!);
    expect(sources[0]!.closed).toBe(true);
    expect(sources).toHaveLength(1);
    vi.advanceTimersByTime(1_000);
    expect(sources).toHaveLength(2);
    sources[1]!.emit("open");
    expect(spy).toHaveBeenCalledWith();
  });

  it("doubles the backoff up to a cap, and resets it after a successful open", () => {
    const { sources } = setupMany();
    for (const delay of [1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000]) {
      const before = sources.length;
      giveUp(sources[before - 1]!);
      vi.advanceTimersByTime(delay - 1);
      expect(sources).toHaveLength(before);
      vi.advanceTimersByTime(1);
      expect(sources).toHaveLength(before + 1);
    }
    const last = sources[sources.length - 1]!;
    last.emit("open");
    giveUp(last);
    vi.advanceTimersByTime(1_000);
    expect(sources).toHaveLength(9);
  });

  it("cancels a pending reopen on cleanup", () => {
    const { sources, close } = setupMany();
    giveUp(sources[0]!);
    close();
    vi.advanceTimersByTime(120_000);
    expect(sources).toHaveLength(1);
  });
});

describe("useLiveUpdates (AC-117, AC-122)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("opens the stream only once the Owner is signed in, and closes it on sign-out", () => {
    const sources: FakeSource[] = [];
    vi.stubGlobal("EventSource", function Source(url: string) {
      const source = new FakeSource(url);
      sources.push(source);
      return source;
    });
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client }, children);
    renderHook(() => useLiveUpdates(), { wrapper });
    expect(sources).toHaveLength(0);

    act(() => client.setQueryData(linkedAccountsKey, []));
    expect(sources).toHaveLength(1);
    expect(sources[0]!.closed).toBe(false);

    act(() => client.clear());
    expect(sources[0]!.closed).toBe(true);
  });
});
