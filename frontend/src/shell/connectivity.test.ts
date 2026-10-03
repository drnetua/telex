import { onlineManager, QueryClient, QueryObserver } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  connectivity,
  isShellActive,
  resetConnectivity,
  retryNow,
  setShellActive,
} from "./connectivity";

describe("connectivity state (AC-176, AC-177)", () => {
  beforeEach(() => resetConnectivity());
  afterEach(() => vi.unstubAllGlobals());

  it("starts online and notifies subscribers on change only", () => {
    const seen: string[] = [];
    const off = connectivity.subscribe(() => seen.push(connectivity.get()));
    expect(connectivity.get()).toBe("online");
    connectivity.reportNoAnswer();
    connectivity.reportNoAnswer();
    expect(seen).toEqual(["not-responding"]);
    off();
    connectivity.reportAnswered();
    expect(seen).toEqual(["not-responding"]);
  });

  it("not-responding pauses queries and an answer restores online", () => {
    connectivity.reportNoAnswer();
    expect(onlineManager.isOnline()).toBe(false);
    connectivity.reportAnswered();
    expect(connectivity.get()).toBe("online");
    expect(onlineManager.isOnline()).toBe(true);
  });

  it("paused queries refetch when the connection comes back (AC-176)", async () => {
    connectivity.reportNoAnswer();
    const queryFn = vi.fn().mockResolvedValue("fresh");
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.mount();
    const observer = new QueryObserver(client, { queryKey: ["screen"], queryFn });
    const off = observer.subscribe(() => undefined);
    await Promise.resolve();
    expect(queryFn).not.toHaveBeenCalled();
    expect(observer.getCurrentResult().fetchStatus).toBe("paused");
    connectivity.reportAnswered();
    await vi.waitFor(() => expect(observer.getCurrentResult().data).toBe("fresh"));
    expect(queryFn).toHaveBeenCalledTimes(1);
    off();
    client.unmount();
    client.clear();
  });

  it("browser offline event sets offline; online event clears offline only", () => {
    window.dispatchEvent(new Event("offline"));
    expect(connectivity.get()).toBe("offline");
    expect(onlineManager.isOnline()).toBe(false);
    window.dispatchEvent(new Event("online"));
    expect(connectivity.get()).toBe("online");
    connectivity.reportNoAnswer();
    window.dispatchEvent(new Event("online"));
    expect(connectivity.get()).toBe("not-responding");
  });

  it("tracks whether the shell is active", () => {
    expect(isShellActive()).toBe(false);
    setShellActive(true);
    expect(isShellActive()).toBe(true);
  });

  it("retryNow fires one pulse and reports stillDown while there is no answer", async () => {
    connectivity.reportNoAnswer();
    const f = vi.fn().mockRejectedValue(new TypeError("network"));
    vi.stubGlobal("fetch", f);
    await expect(retryNow()).resolves.toEqual({ stillDown: true });
    expect(f).toHaveBeenCalledTimes(1);
    expect(f.mock.calls[0]![0]).toBe("/api/v1/pulse");
    expect(connectivity.get()).toBe("not-responding");
  });

  it("retryNow restores online when the pulse answers", async () => {
    connectivity.reportNoAnswer();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(Response.json({ inboxCount: 0, conditions: [] })),
    );
    await expect(retryNow()).resolves.toEqual({ stillDown: false });
    expect(connectivity.get()).toBe("online");
  });
});
