import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiFailure } from "./client";

const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ type: `urn:telex:error:${code}`, code, status }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });

describe("apiFetch (AC-101, AC-102, AC-96, AC-93)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    document.cookie = "XSRF-TOKEN=tok123";
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("returns parsed JSON on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ ok: 1 })));
    await expect(apiFetch("/api/v1/me")).resolves.toEqual({ ok: 1 });
  });

  it("sends the CSRF header on POST and the background marker only when asked", async () => {
    const f = vi.fn().mockResolvedValue(Response.json({}));
    vi.stubGlobal("fetch", f);
    await apiFetch("/api/v1/x", { method: "POST" });
    await apiFetch("/api/v1/me", { background: true });
    await apiFetch("/api/v1/me");
    const h = (i: number) => new Headers(f.mock.calls[i]![1].headers);
    expect(h(0).get("X-XSRF-TOKEN")).toBe("tok123");
    expect(h(0).get("X-Telex-Background")).toBeNull();
    expect(h(1).get("X-Telex-Background")).toBe("1");
    expect(h(2).get("X-Telex-Background")).toBeNull();
  });

  it.each([
    [401, "unauthenticated", "sign-in"],
    [401, "session-ended", "session-ended"],
    [403, "forbidden", "unavailable"],
    [500, "internal-error", "unavailable"],
    [503, "mail-unavailable", "unavailable"],
  ])("routes %i %s to %s", async (status, code, route) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(problem(status, code)));
    await expect(apiFetch("/api/v1/me")).rejects.toMatchObject({ route, code });
  });

  it("hands other problems back to the caller without routing", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(problem(422, "invalid-email")));
    const err = await apiFetch("/api/v1/x", { method: "POST" }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiFailure);
    expect(err.route).toBeUndefined();
    expect(err.code).toBe("invalid-email");
  });

  it("aborts after 10 s with no answer and routes to unavailable", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        (_u: string, init: RequestInit) =>
          new Promise((_, reject) =>
            init.signal?.addEventListener("abort", () =>
              reject(new DOMException("a", "AbortError")),
            ),
          ),
      ),
    );
    const p = apiFetch("/api/v1/me");
    const assertion = expect(p).rejects.toMatchObject({ route: "unavailable" });
    await vi.advanceTimersByTimeAsync(10_000);
    await assertion;
  });
});
