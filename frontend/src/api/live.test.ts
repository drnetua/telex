import { QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isBackground } from "./account";
import { openLiveUpdates } from "./live";

class FakeSource {
  closed = false;
  private listeners = new Map<string, ((e: Event) => void)[]>();
  constructor(readonly url: string) {}
  addEventListener(type: string, fn: (e: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), fn]);
  }
  close() {
    this.closed = true;
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
