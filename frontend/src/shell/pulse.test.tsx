import { onlineManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { connectivity, resetConnectivity, setShellActive } from "./connectivity";
import { failureBus, createAppQueryClient } from "../app/queryClient";
import { usePulse } from "./pulse";

const ok = () => Response.json({ inboxCount: 3, conditions: [] });

function wrapper(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", { configurable: true, get: () => state });
}

const hang = (_u: string, init: RequestInit) =>
  new Promise((_, reject) =>
    init.signal?.addEventListener("abort", () => reject(new DOMException("a", "AbortError"))),
  );

describe("usePulse (AC-176)", () => {
  let client: QueryClient;
  beforeEach(() => {
    vi.useFakeTimers();
    resetConnectivity();
    setShellActive(true);
    setVisibility("visible");
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });
  afterEach(() => {
    client.clear();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("polls every 3 s with the background header", async () => {
    const f = vi.fn().mockImplementation(() => Promise.resolve(ok()));
    vi.stubGlobal("fetch", f);
    const { result } = renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(f).toHaveBeenCalledTimes(1);
    expect(result.current.data?.inboxCount).toBe(3);
    expect(new Headers(f.mock.calls[0]![1].headers).get("X-Telex-Background")).toBe("1");
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(f).toHaveBeenCalledTimes(2);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(f).toHaveBeenCalledTimes(3);
  });

  it("counts no answer within 2 s as not-responding and keeps polling while queries are paused", async () => {
    const f = vi.fn(hang);
    vi.stubGlobal("fetch", f);
    renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(2000));
    expect(connectivity.get()).toBe("not-responding");
    expect(onlineManager.isOnline()).toBe(false);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(f.mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it("restores online on the first success", async () => {
    const f = vi
      .fn()
      .mockRejectedValueOnce(new TypeError("network"))
      .mockImplementation(() => Promise.resolve(ok()));
    vi.stubGlobal("fetch", f);
    renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(connectivity.get()).toBe("not-responding");
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(connectivity.get()).toBe("online");
    expect(onlineManager.isOnline()).toBe(true);
  });

  it("stops polling after 401 session-ended and never reports not-responding", async () => {
    const f = vi.fn().mockImplementation(() =>
      Promise.resolve(
        new Response(JSON.stringify({ code: "session-ended" }), {
          status: 401,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );
    vi.stubGlobal("fetch", f);
    const { result } = renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(result.current.error).toMatchObject({ route: "session-ended" });
    await act(() => vi.advanceTimersByTimeAsync(9000));
    expect(f).toHaveBeenCalledTimes(1);
    expect(connectivity.get()).toBe("online");
  });

  it("does not poll while hidden and fires at once when visible again", async () => {
    const f = vi.fn().mockImplementation(() => Promise.resolve(ok()));
    vi.stubGlobal("fetch", f);
    renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    setVisibility("hidden");
    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
      await vi.advanceTimersByTimeAsync(9000);
    });
    expect(f).toHaveBeenCalledTimes(1);
    setVisibility("visible");
    await act(async () => {
      document.dispatchEvent(new Event("visibilitychange"));
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(f).toHaveBeenCalledTimes(2);
  });
});

/** Failed-pulse blocks: the fetch stub, timers and the failure handler are restored even when an assertion fails. */
function failedPulseSetup(shellActive: boolean) {
  vi.useFakeTimers();
  resetConnectivity();
  setShellActive(shellActive);
  setVisibility("visible");
  const handler = vi.fn();
  failureBus.handler = handler;
  const client = createAppQueryClient();
  return { handler, client };
}

function failedPulseCleanup(client: QueryClient | undefined) {
  client?.clear();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  failureBus.handler = () => undefined;
}

describe("a pulse answered 5xx (AC-176)", () => {
  let client: QueryClient | undefined;
  afterEach(() => failedPulseCleanup(client));

  it("reports not-responding and does not route to SCR-93", async () => {
    const setup = failedPulseSetup(true);
    client = setup.client;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ code: "internal-error" }, { status: 500 })),
    );
    renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(connectivity.get()).toBe("not-responding");
    expect(setup.handler).not.toHaveBeenCalled();
  });
});

describe("a failed pulse where the shell is not mounted (AC-122, AC-176)", () => {
  let client: QueryClient | undefined;
  afterEach(() => failedPulseCleanup(client));

  it.each([
    ["no answer", () => Promise.reject(new TypeError("network"))],
    ["502", () => Promise.resolve(Response.json({ code: "bad-gateway" }, { status: 502 }))],
    ["503", () => Promise.resolve(Response.json({ code: "unavailable" }, { status: 503 }))],
    ["504", () => Promise.resolve(Response.json({ code: "gateway-timeout" }, { status: 504 }))],
    ["500", () => Promise.resolve(Response.json({ code: "internal-error" }, { status: 500 }))],
  ])("%s reports not-responding and never reaches the failure bus", async (_name, reply) => {
    const setup = failedPulseSetup(false);
    client = setup.client;
    vi.stubGlobal("fetch", vi.fn().mockImplementation(reply));
    const { result } = renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(result.current.error).toMatchObject({ route: undefined });
    expect(connectivity.get()).toBe("not-responding");
    expect(setup.handler).not.toHaveBeenCalled();
  });
});

describe("a pulse answered 403 (AC-122)", () => {
  let client: QueryClient | undefined;
  afterEach(() => failedPulseCleanup(client));

  it("does not take over the screen where the shell is not mounted", async () => {
    const setup = failedPulseSetup(false);
    client = setup.client;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ code: "forbidden" }, { status: 403 })),
    );
    const { result } = renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(result.current.error).toMatchObject({ status: 403, route: undefined });
    expect(setup.handler).not.toHaveBeenCalled();
  });

  it("goes to SCR-93 with Retry while the shell is mounted", async () => {
    const setup = failedPulseSetup(true);
    client = setup.client;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ code: "forbidden" }, { status: 403 })),
    );
    const { result } = renderHook(() => usePulse(), { wrapper: wrapper(client) });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(result.current.error).toMatchObject({ status: 403, route: "unavailable" });
    expect(setup.handler).toHaveBeenCalledWith(
      expect.objectContaining({ status: 403, route: "unavailable" }),
      expect.any(Function),
      "query",
    );
  });
});
