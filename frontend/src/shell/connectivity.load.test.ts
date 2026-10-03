import { afterEach, describe, expect, it, vi } from "vitest";

// Its own file: the module reads the device's network state once, as it loads.
describe("connectivity on load (AC-176)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("starts offline when the device is already offline as teleX loads", async () => {
    vi.stubGlobal("navigator", { ...navigator, onLine: false });
    const { connectivity } = await import("./connectivity");
    const { onlineManager } = await import("@tanstack/react-query");
    expect(connectivity.get()).toBe("offline");
    expect(onlineManager.isOnline()).toBe(false);
    connectivity.reportNoAnswer();
    expect(connectivity.get()).toBe("offline");
  });
});
