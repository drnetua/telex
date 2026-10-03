import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiFailure } from "./client";
import { formatMaskedPhone, listMyLinkedAccounts, unlinkMyLinkedAccount } from "./linkedAccounts";
import {
  cancelMyLinkingAttempt,
  getMyLinkingAttempt,
  resendLinkingCode,
  startMyLinkingAttempt,
  submitLinkingCode,
  submitLinkingPassword,
  submitLinkingPhone,
} from "./linking";
import { messages } from "../messages";

const problem = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify({ type: `urn:telex:error:${body.code}`, status, ...body }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });

describe("linked account and linking clients", () => {
  let f: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=tok";
    f = vi.fn().mockResolvedValue(Response.json({ items: [] }));
    vi.stubGlobal("fetch", f);
  });
  afterEach(() => vi.unstubAllGlobals());

  const call = (i = 0) => ({
    url: f.mock.calls[i]![0] as string,
    init: f.mock.calls[i]![1] as RequestInit,
    headers: new Headers((f.mock.calls[i]![1] as RequestInit).headers),
  });

  it("lists accounts and sends the background marker when asked (AC-116, AC-121)", async () => {
    await expect(listMyLinkedAccounts(true)).resolves.toEqual([]);
    expect(call().url).toBe("/api/v1/linked-accounts");
    expect(call().headers.get("X-Telex-Background")).toBe("1");
  });

  it("unlinks by id with CSRF", async () => {
    f.mockResolvedValue(Response.json({ signOutConfirmed: false }));
    await expect(unlinkMyLinkedAccount("a/b")).resolves.toEqual({ signOutConfirmed: false });
    expect(call().url).toBe("/api/v1/linked-accounts/a%2Fb");
    expect(call().init.method).toBe("DELETE");
    expect(call().headers.get("X-XSRF-TOKEN")).toBe("tok");
  });

  it("formats the masked phone (AC-01)", () => {
    expect(formatMaskedPhone({ countryCode: "999", lastDigits: "00" })).toBe("+999 ••• ••00");
  });

  it("calls every linking operation on its path", async () => {
    f.mockImplementation(() => Promise.resolve(Response.json({})));
    await getMyLinkingAttempt();
    await startMyLinkingAttempt({ origin: "accounts", targetLinkedAccountId: "x" });
    await submitLinkingPhone("+1");
    await submitLinkingCode("123");
    await resendLinkingCode();
    await submitLinkingPassword("pw");
    f.mockResolvedValue(new Response(null, { status: 204 }));
    await cancelMyLinkingAttempt();
    const seen = [0, 1, 2, 3, 4, 5, 6].map((i) => `${call(i).init.method ?? "GET"} ${call(i).url}`);
    expect(seen).toEqual([
      "GET /api/v1/linking-attempt",
      "POST /api/v1/linking-attempt",
      "POST /api/v1/linking-attempt/phone",
      "POST /api/v1/linking-attempt/code",
      "POST /api/v1/linking-attempt/code/resend",
      "POST /api/v1/linking-attempt/password",
      "DELETE /api/v1/linking-attempt",
    ]);
    expect(JSON.parse(call(1).init.body as string)).toEqual({
      origin: "accounts",
      targetLinkedAccountId: "x",
    });
    expect(JSON.parse(call(2).init.body as string)).toEqual({ phoneNumber: "+1" });
    expect(call(2).headers.get("Content-Type")).toBe("application/json");
  });

  it("keeps the typed problem extensions on the failure", async () => {
    f.mockResolvedValue(
      problem(429, { code: "telegram-wait-required", retryAt: "2026-10-03T10:15:00Z" }),
    );
    await expect(submitLinkingCode("1")).rejects.toMatchObject({
      status: 429,
      code: "telegram-wait-required",
      extras: { retryAt: "2026-10-03T10:15:00Z" },
    });
    f.mockResolvedValue(
      problem(422, { code: "telegram-password-wrong", passwordHint: "Test hint" }),
    );
    await expect(submitLinkingPassword("x")).rejects.toMatchObject({
      extras: { passwordHint: "Test hint" },
    });
    f.mockResolvedValue(problem(409, { code: "linked-account-limit-reached", limit: 3 }));
    await expect(startMyLinkingAttempt({ origin: "inbox" })).rejects.toMatchObject({
      extras: { limit: 3 },
    });
    f.mockResolvedValue(problem(409, { code: "linking-step-mismatch", step: "code" }));
    const error = await submitLinkingPhone("1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiFailure);
    expect((error as ApiFailure).extras.step).toBe("code");
  });

  it("routes only unmapped failures; screen-handled codes carry no route", async () => {
    f.mockResolvedValue(problem(503, { code: "telegram-unavailable" }));
    await expect(submitLinkingPhone("1")).rejects.toMatchObject({ route: undefined });
    f.mockResolvedValue(problem(503, { code: "telegram-linking-not-set-up" }));
    await expect(startMyLinkingAttempt({ origin: "inbox" })).rejects.toMatchObject({
      route: undefined,
    });
    f.mockResolvedValue(problem(503, { code: "internal-error" }));
    await expect(submitLinkingPhone("1")).rejects.toMatchObject({ route: "unavailable" });
  });
});

describe("new copy", () => {
  const codes = [
    "telegram-linking-not-set-up",
    "linked-account-limit-reached",
    "linking-attempt-not-found",
    "linking-step-mismatch",
    "telegram-phone-invalid",
    "telegram-phone-unregistered",
    "telegram-phone-banned",
    "telegram-code-wrong",
    "telegram-code-expired",
    "telegram-wait-required",
    "telegram-password-wrong",
    "telegram-unavailable",
    "telegram-account-owned-by-another-owner",
    "telegram-account-already-linked",
    "telegram-account-mismatch",
  ];
  it.each(codes)("has a catalog entry for %s", (code) => {
    expect(messages.linking.problems).toHaveProperty(code);
  });
  it("has the banner and accounts groups with sentence-case, emoji-free copy", () => {
    expect(messages.banner.one("Test User")).toBe(
      "Test User's Telegram is disconnected. teleX can't work with it until you sign in again.",
    );
    expect(messages.banner.several(2)).toBe("2 Telegram accounts are disconnected.");
    expect(messages.accounts.syncing(312, 480)).toBe("Syncing chats: 312 of 480");
    expect(messages.accounts.chats(57)).toBe("57 chats");
    expect(messages.accounts.reconnecting).toBe("Reconnecting");
    expect(messages.accounts.sessionLost).toBe("Session lost");
  });
});
