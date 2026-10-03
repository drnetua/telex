import { expect, type Page } from "@playwright/test";

/**
 * Scenarios of the `fake` Telegram adapter, picked by the digit after `99966` in its test-number shape
 * `99966XYYYY` (never a real-looking number). The `fake` adapter must be on: `TELEX_TELEGRAM_ADAPTER=fake`.
 */
export const SCENARIO = {
  plain: "0",
  twoStep: "1",
  floodOnPhone: "5",
  floodOnCode: "6",
  /** Ends the session about a second after the link completed (the Owner ends it in Telegram). */
  terminateAfterLink: "9",
} as const;

export const CODE = "12345";
export const WRONG_CODE = "11111";
export const EXPIRED_CODE = "00000";
export const PASSWORD = "secret";

/** A unique test number: Telegram accounts are unique per installation, so every test brings its own. */
export function testNumber(scenario: (typeof SCENARIO)[keyof typeof SCENARIO]): {
  digits: string;
  displayName: string;
} {
  const chats = String(Math.floor(Math.random() * 900) + 10).padStart(4, "0");
  return { digits: `99966${scenario}${chats}`, displayName: `Test user ${chats}` };
}

export async function typeCode(page: Page, code: string): Promise<void> {
  await page.getByLabel("Digit 1").first().fill(code);
}

/** Inbox "Connect Telegram" → phone step → code step. */
export async function startAndSendPhone(page: Page, digits: string): Promise<void> {
  await page.getByRole("button", { name: "Connect Telegram" }).click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  await expect(page.getByRole("heading", { name: "Connect your Telegram" })).toBeVisible();
  await page.getByLabel("Phone number").fill(`+${digits}`);
  await page.getByRole("button", { name: "Send code" }).click();
}

export async function expectCodeStep(page: Page): Promise<void> {
  await expect(page.getByRole("group", { name: "Login code" })).toBeVisible();
}
