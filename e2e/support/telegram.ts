import { expect, type Page } from "@playwright/test";

/**
 * Scenarios of the `fake` Telegram adapter, picked by the first six digits of its test-number shape `PPPPPXYYYY`
 * (never a real-looking number; YYYY is the chat count). The `fake` adapter must be on:
 * `TELEX_TELEGRAM_ADAPTER=fake`.
 */
export const SCENARIO = {
  plain: "999660",
  twoStep: "999661",
  /** An unregistered number: Telegram has no account for it. */
  unregistered: "999664",
  /** A banned number. */
  banned: "999663",
  /** Registered as far as the phone step knows; Telegram finds it unregistered once the code is checked. */
  unregisteredAfterCode: "999650",
  /** Refuses "Send a new code" as an invalid number (the attempt ends). */
  resendInvalid: "999630",
  floodOnPhone: "999665",
  floodOnCode: "999666",
  /** Ends the session about a second after the link completed (the Owner ends it in Telegram), every time. */
  terminateAfterLink: "999669",
  /** Like `terminateAfterLink`, but only the first session: the one made by signing in again survives. */
  terminateOnce: "999641",
  /** Telegram becomes unreachable a moment after the link, then reachable again (Reconnecting, then Connected). */
  outage: "999640",
} as const;

export const CODE = "12345";
export const WRONG_CODE = "11111";
export const EXPIRED_CODE = "00000";
export const PASSWORD = "secret";

/** A unique test number: Telegram accounts are unique per installation, so every test brings its own. */
export function testNumber(
  scenario: (typeof SCENARIO)[keyof typeof SCENARIO],
): {
  digits: string;
  displayName: string;
  chats: number;
} {
  const chats = String(Math.floor(Math.random() * 900) + 10).padStart(4, "0");
  return {
    digits: `${scenario}${chats}`,
    displayName: `Test user ${chats}`,
    chats: Number(chats),
  };
}

export async function typeCode(page: Page, code: string): Promise<void> {
  await page.getByLabel("Digit 1").first().fill(code);
}

/** Inbox "Connect Telegram" → phone step → code step. */
export async function startAndSendPhone(
  page: Page,
  digits: string,
): Promise<void> {
  await page.getByRole("button", { name: "Connect Telegram" }).click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  await expect(
    page.getByRole("heading", { name: "Connect your Telegram" }),
  ).toBeVisible();
  await page.getByLabel("Phone number").fill(`+${digits}`);
  await page.getByRole("button", { name: "Send code" }).click();
}

export async function expectCodeStep(page: Page): Promise<void> {
  await expect(page.getByRole("group", { name: "Login code" })).toBeVisible();
}
