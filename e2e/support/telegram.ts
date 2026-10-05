import { expect, type Page } from "@playwright/test";
import { closeSync, openSync } from "node:fs";
import { join } from "node:path";

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

/** Suffixes 10..909: YYYY is also the account's chat count, so it stays small enough to sync quickly. */
const FIRST_SUFFIX = 10;
const SUFFIXES = 900;

/**
 * A test number no other test of this run uses: Telegram accounts are unique per installation, and the suite runs
 * fully parallel in two projects (with retries) against one installation. Each number is claimed by creating a file
 * exclusively in the run's claim directory (`global-setup.ts`), which every worker shares; the scan starts at the
 * run's random offset. Single machine only: shards would each have their own directory.
 */
export function testNumber(
  scenario: (typeof SCENARIO)[keyof typeof SCENARIO],
): {
  digits: string;
  displayName: string;
  chats: number;
} {
  const dir = process.env.TELEX_E2E_NUMBER_CLAIMS;
  if (!dir)
    throw new Error(
      "TELEX_E2E_NUMBER_CLAIMS is not set: run the suite through playwright.config.ts",
    );
  const offset = Number(process.env.TELEX_E2E_NUMBER_OFFSET ?? "0");
  for (let i = 0; i < SUFFIXES; i++) {
    const n = FIRST_SUFFIX + ((offset + i) % SUFFIXES);
    try {
      closeSync(openSync(join(dir, `${scenario}-${n}`), "wx"));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "EEXIST") continue;
      throw error;
    }
    const chats = String(n).padStart(4, "0");
    return {
      digits: `${scenario}${chats}`,
      displayName: `Test user ${chats}`,
      chats: n,
    };
  }
  throw new Error(`no free test number left for scenario ${scenario}`);
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
