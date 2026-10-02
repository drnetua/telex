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

  it("keeps the first destination and ignores later ones, such as sign-in from parallel 401s", () => {
    rememberDestination("/profile#sessions");
    rememberDestination("/sign-in");
    rememberDestination("/inbox");
    expect(takeRememberedDestination()).toBe("/profile#sessions");
  });

  it.each([
    "/sign-in",
    "/sign-in/check-email",
    "/sign-in/link?t=1",
    "/welcome/passkey",
    "/session-ended",
  ])("never remembers the auth page %s", (page) => {
    rememberDestination(page);
    expect(takeRememberedDestination()).toBe("/inbox");
  });

  it("defaults to the Inbox when nothing was remembered", () => {
    expect(takeRememberedDestination()).toBe("/inbox");
  });
});
