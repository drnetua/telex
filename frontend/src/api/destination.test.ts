import { beforeEach, describe, expect, it } from "vitest";
import { rememberDestination, takeRememberedDestination } from "./destination";

describe("remembered destination (AC-101)", () => {
  beforeEach(() => localStorage.clear());

  it("returns an accepted relative path, including hash, and clears it on read", () => {
    rememberDestination("/profile?x=1#sessions");
    expect(takeRememberedDestination()).toBe("/profile?x=1#sessions");
    expect(takeRememberedDestination()).toBe("/inbox");
  });

  it.each(["//evil.example/x", "https://evil.example/x", "profile", "/\\evil.example"])(
    "rejects %s and falls back to the Inbox",
    (bad) => {
      rememberDestination(bad);
      expect(takeRememberedDestination()).toBe("/inbox");
    },
  );

  it("defaults to the Inbox when nothing was remembered", () => {
    expect(takeRememberedDestination()).toBe("/inbox");
  });
});
