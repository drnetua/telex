import { beforeEach, describe, expect, it } from "vitest";
import { rememberDestination } from "../api/destination";
import { landAfterSignIn } from "./landing";

beforeEach(() => localStorage.clear());

describe("landing after sign-in", () => {
  it("AC-173: a created account goes to the passkey offer, keeping the remembered page", () => {
    rememberDestination("/profile#sessions");
    expect(landAfterSignIn(true)).toBe("/welcome/passkey");
    expect(localStorage.getItem("telex.destination")).toBe("/profile#sessions");
  });

  it("AC-101: an existing account lands on the remembered teleX page", () => {
    rememberDestination("/profile#sessions");
    expect(landAfterSignIn(false)).toBe("/profile#sessions");
  });

  it("AC-101: nothing remembered leads to the Inbox", () => {
    expect(landAfterSignIn(false)).toBe("/inbox");
  });

  it.each(["https://evil.example/x", "//evil.example", "javascript:alert(1)"])(
    "AC-101: foreign destination %s leads to the Inbox",
    (dest) => {
      rememberDestination(dest);
      expect(landAfterSignIn(false)).toBe("/inbox");
    },
  );
});
