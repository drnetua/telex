import {
  focusManager,
  onlineManager,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { meKey, useMe, type Me } from "./account";

afterEach(() => vi.unstubAllGlobals());

describe("useMe (AC-96, ADR-0005)", () => {
  function setup() {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => Promise.resolve(Response.json({ ownerId: "o1" })));
    vi.stubGlobal("fetch", fetchMock);
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const header = (i: number) => new Headers(fetchMock.mock.calls[i]?.[1].headers);
    return { fetchMock, client, wrapper, header };
  }

  it("marks a window-focus refetch, but not the first load, as background", async () => {
    const { client, wrapper, header } = setup();
    renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(client.getQueryData(meKey)).toBeDefined());
    await act(async () => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });
    await waitFor(() => expect(client.getQueryState(meKey)?.dataUpdateCount).toBe(2));
    expect(header(0).get("X-Telex-Background")).toBeNull();
    expect(header(1).get("X-Telex-Background")).toBe("1");
  });

  it("marks a reconnect refetch as background", async () => {
    const { client, wrapper, header } = setup();
    renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(client.getQueryData(meKey)).toBeDefined());
    await act(async () => {
      onlineManager.setOnline(false);
      onlineManager.setOnline(true);
    });
    await waitFor(() => expect(client.getQueryState(meKey)?.dataUpdateCount).toBe(2));
    expect(header(1).get("X-Telex-Background")).toBe("1");
  });

  it("does not mark a refetch on remount (page open) as background", async () => {
    const { client, wrapper, header } = setup();
    const first = renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(client.getQueryData(meKey)).toBeDefined());
    first.unmount();
    await new Promise((r) => setTimeout(r, 20));
    renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(client.getQueryState(meKey)?.dataUpdateCount).toBe(2));
    expect(header(1).get("X-Telex-Background")).toBeNull();
  });
});

describe("Me theme fields (AC-181)", () => {
  it("parses theme, timeZone and timeZoneIsFallback from /api/v1/me", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() =>
        Promise.resolve(
          Response.json({
            ownerId: "o1",
            email: "a@b.c",
            linkedAccountCount: 0,
            theme: "dark",
            timeZone: "Europe/Kyiv",
            timeZoneIsFallback: true,
          }),
        ),
      ),
    );
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(result.current.data).toBeDefined());
    const me: Me | undefined = result.current.data;
    expect(me?.theme).toBe("dark");
    expect(me?.timeZone).toBe("Europe/Kyiv");
    expect(me?.timeZoneIsFallback).toBe(true);
  });
});
