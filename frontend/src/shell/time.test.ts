import { QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Me } from "../api/account";
import { createAppQueryClient } from "../app/queryClient";
import { deviceTimeZone, formatInstant, useSaveDetectedTimeZone } from "./time";

const baseMe: Me = {
  ownerId: "o1",
  email: "me@example.com",
  linkedAccountCount: 0,
  theme: "light",
  timeZone: null,
  timeZoneIsFallback: false,
};

function stubFetch() {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() =>
    Promise.resolve(
      new Response(
        JSON.stringify({ theme: "light", timeZone: "Europe/Kyiv", timeZoneIsFallback: false }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function wrapper() {
  const client = createAppQueryClient();
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client }, children);
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("formatInstant", () => {
  it("shows the Owner-zone date across the date line", () => {
    const instant = "2026-10-02T23:00:00Z";
    expect(formatInstant(instant, "UTC")).toBe("2 Oct 2026");
    expect(formatInstant(instant, "Pacific/Auckland")).toBe("3 Oct 2026");
  });

  it("falls back to the device zone when none is saved", () => {
    expect(formatInstant("2026-10-02T12:00:00Z", null)).toMatch(/Oct 2026$/);
  });
});

describe("deviceTimeZone", () => {
  it("returns null when Intl throws", () => {
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(() => {
      throw new Error("no intl");
    });
    expect(deviceTimeZone()).toBeNull();
  });

  it("returns null when the zone is undefined", () => {
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(
      () =>
        ({ resolvedOptions: () => ({ timeZone: undefined }) }) as unknown as Intl.DateTimeFormat,
    );
    expect(deviceTimeZone()).toBeNull();
  });
});

describe("useSaveDetectedTimeZone", () => {
  it("sends the device zone once while none is saved", async () => {
    const fetchMock = stubFetch();
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(
      () =>
        ({
          resolvedOptions: () => ({ timeZone: "Europe/Kyiv" }),
        }) as unknown as Intl.DateTimeFormat,
    );
    const { rerender } = renderHook(({ me }) => useSaveDetectedTimeZone(me), {
      wrapper: wrapper(),
      initialProps: { me: baseMe as Me | undefined },
    });
    rerender({ me: { ...baseMe } });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("/api/v1/me/preferences/detected-time-zone");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ timeZone: "Europe/Kyiv" });
    expect(new Headers(init?.headers).get("X-Telex-Background")).toBe("1");
  });

  it("sends null when the device zone is unreadable", async () => {
    const fetchMock = stubFetch();
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(() => {
      throw new Error("no intl");
    });
    renderHook(() => useSaveDetectedTimeZone(baseMe), { wrapper: wrapper() });
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(JSON.parse(fetchMock.mock.calls[0]![1]?.body as string)).toEqual({ timeZone: null });
  });

  it("sends nothing when a zone is saved or me is not loaded", async () => {
    const fetchMock = stubFetch();
    renderHook(() => useSaveDetectedTimeZone({ ...baseMe, timeZone: "UTC" }), {
      wrapper: wrapper(),
    });
    renderHook(() => useSaveDetectedTimeZone(undefined), { wrapper: wrapper() });
    await new Promise((r) => setTimeout(r, 20));
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
