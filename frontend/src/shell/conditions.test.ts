import { afterEach, describe, expect, it, vi } from "vitest";
import { orderConditions, resetUnknownConditions } from "./conditions";

afterEach(() => {
  vi.restoreAllMocks();
  resetUnknownConditions();
});

describe("orderConditions", () => {
  it("orders by the fixed importance", () => {
    const codes = [
      "triage-deferred",
      "budget-exhausted",
      "offline",
      "bot-blocked",
      "account-disconnected",
      "consent-needed",
      "all-assistants-paused",
    ];
    expect(orderConditions(codes).map((c) => c.code)).toEqual([
      "offline",
      "account-disconnected",
      "bot-blocked",
      "consent-needed",
      "budget-exhausted",
      "all-assistants-paused",
      "triage-deferred",
    ]);
  });

  it("puts offline and not-responding above every server condition", () => {
    expect(orderConditions(["account-disconnected", "not-responding"])[0]?.code).toBe(
      "not-responding",
    );
  });

  it("drops an unknown code and logs it once", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    expect(orderConditions(["foo-bar"])).toEqual([]);
    orderConditions(["foo-bar", "offline"]);
    expect(warn).toHaveBeenCalledTimes(1);
  });
});
