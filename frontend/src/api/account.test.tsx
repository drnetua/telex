import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { meKey, useMe } from "./account";

afterEach(() => vi.unstubAllGlobals());

describe("useMe (AC-96, ADR-0005)", () => {
  it("marks refetches, but not the first load, as background", async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => Promise.resolve(Response.json({ ownerId: "o1" })));
    vi.stubGlobal("fetch", fetchMock);
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    renderHook(() => useMe(), { wrapper });
    await waitFor(() => expect(client.getQueryData(meKey)).toBeDefined());
    await client.refetchQueries({ queryKey: meKey });
    const header = (i: number) => new Headers(fetchMock.mock.calls[i]?.[1].headers);
    expect(header(0).get("X-Telex-Background")).toBeNull();
    expect(header(1).get("X-Telex-Background")).toBe("1");
  });
});
