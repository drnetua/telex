import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useUnlinkAccount } from "./linkedAccounts";

afterEach(() => vi.unstubAllGlobals());

describe("useUnlinkAccount", () => {
  it("AC-122: invalidates the pulse so the banner cannot outlive the last Session lost account", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ signOutConfirmed: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const spy = vi.spyOn(client, "invalidateQueries");
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useUnlinkAccount(), { wrapper });
    result.current.mutate("a1");
    await waitFor(() => expect(spy).toHaveBeenCalledWith({ queryKey: ["pulse"] }));
  });
});
