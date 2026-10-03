import { expect, test, type Page } from "@playwright/test";
import { expectNoA11yViolations, signUp } from "../support/flows";
import {
  CODE,
  EXPIRED_CODE,
  PASSWORD,
  SCENARIO,
  WRONG_CODE,
  expectCodeStep,
  startAndSendPhone,
  testNumber,
  typeCode,
} from "../support/telegram";

// Runs against the `fake` Telegram adapter (TELEX_TELEGRAM_ADAPTER=fake) at 360 px and 1280 px (see the projects).

const connectButton = (page: Page) => page.getByRole("button", { name: "Connect Telegram" });
const accountLine = (page: Page, name: string) => page.getByRole("link", { name: new RegExp(name) });

test("AC-01: link an account with two-step verification, see it syncing in the Inbox", async ({
  page,
}) => {
  const { digits, displayName } = testNumber(SCENARIO.twoStep);
  await signUp(page);
  await expect(connectButton(page)).toBeVisible();
  await expectNoA11yViolations(page, "SCR-10 Inbox without accounts");

  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await expectNoA11yViolations(page, "SCR-02 code step");

  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("Hint: first pet")).toBeVisible();
  await expectNoA11yViolations(page, "SCR-02 password step");

  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page).toHaveURL(/\/inbox$/);
  const line = accountLine(page, displayName);
  await expect(line).toBeVisible();
  await expect(line).toContainText("+99 ••• ••");
  await expect(line).toContainText(digits.slice(-2));
  await expect(line).toContainText("Connected");
  // Progress is visible while syncing, then the final count.
  await expect(line).toContainText(/Syncing chats|\d+ chats?/);
  await expect(connectButton(page)).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-10 Inbox with an account");

  await page.reload();
  await expect(accountLine(page, displayName)).toBeVisible();
  await expect(connectButton(page)).toHaveCount(0);
});

test("AC-01: an account without two-step verification skips the password step", async ({
  page,
}) => {
  const { digits, displayName } = testNumber(SCENARIO.plain);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await expect(accountLine(page, displayName)).toBeVisible();
  await expect(page.getByLabel("Password")).toHaveCount(0);
});

test("AC-02: a wrong or expired code is explained and can be retried or replaced", async ({
  page,
}) => {
  const { digits, displayName } = testNumber(SCENARIO.plain);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);

  await typeCode(page, WRONG_CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "That code is not right. Try again, or send a new code.",
  );
  await expectNoA11yViolations(page, "SCR-02 wrong code");

  await typeCode(page, EXPIRED_CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("alert")).toContainText("This code has expired. Send a new code.");
  await expectNoA11yViolations(page, "SCR-02 expired code");

  await page.getByRole("button", { name: "Send a new code" }).click();
  await expect(page.getByText("Telegram sent a new code.")).toBeVisible();
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await expect(accountLine(page, displayName)).toBeVisible();
});

test("AC-02: Telegram limiting the attempts ends the attempt with a countdown, and starting again shows the wait", async ({
  page,
}) => {
  const { digits } = testNumber(SCENARIO.floodOnCode);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();

  // The visible text and its screen-reader twin say the same; the first is the visible one.
  const wait = page
    .getByText(/Telegram asks you to wait\. You can try again at \d\d:\d\d, in \d+:\d\d\./)
    .first();
  await expect(page.getByRole("heading", { name: "Too many attempts" })).toBeVisible();
  await expect(wait).toBeVisible();
  const first = await wait.textContent();
  await expect
    .poll(async () => wait.textContent(), { message: "the countdown ticks", timeout: 5_000 })
    .not.toBe(first);
  await expectNoA11yViolations(page, "SCR-02 wait countdown");
});

test("AC-02: starting again with the same number before the wait is over shows the remaining wait, not a code step", async ({
  page,
}) => {
  const { digits } = testNumber(SCENARIO.floodOnPhone);
  const wait = page
    .getByText(/Telegram asks you to wait\. You can try again at \d\d:\d\d, in \d+:\d\d\./)
    .first();
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expect(page.getByRole("heading", { name: "Too many attempts" })).toBeVisible();
  await expect(wait).toBeVisible();

  await page.getByRole("button", { name: "Back" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await startAndSendPhone(page, digits);
  await expect(page.getByRole("heading", { name: "Too many attempts" })).toBeVisible();
  await expect(wait).toBeVisible();
  await expect(page.getByRole("group", { name: "Login code" })).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 remaining wait");
});

test("AC-109: reloading and a second tab continue the wizard at its step; cancelling starts over at the phone", async ({
  page,
  context,
}) => {
  const { digits } = testNumber(SCENARIO.plain);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);

  await page.reload();
  await expectCodeStep(page);

  const other = await context.newPage();
  await other.goto("/connect-telegram");
  await expectCodeStep(other);
  await expect(other.getByLabel("Phone number")).toHaveCount(0);
  await other.close();

  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await connectButton(page).click();
  await expect(page.getByLabel("Phone number")).toBeVisible();
  await expect(page.getByRole("group", { name: "Login code" })).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 phone step after cancel");
});

test("AC-117, AC-122: a lost session shows Session lost and a banner; sign in again; unlink returns to Connect Telegram", async ({
  page,
}) => {
  // This number makes the fake end the session a moment after the link.
  const { digits, displayName } = testNumber(SCENARIO.terminateAfterLink);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  // The fake terminates about a second after the link: the state turns within the live-update window, not the 5 minutes of the AC.
  const line = accountLine(page, displayName);
  await expect(line).toContainText("Session lost", { timeout: 30_000 });
  const banner = page.getByText(`${displayName}'s Telegram is disconnected.`);
  await expect(banner).toBeVisible();
  await expectNoA11yViolations(page, "SCR-10 Inbox with Session lost and banner");

  await line.click();
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(page.getByText("Session lost")).toBeVisible();
  await expect(banner).toBeVisible();
  await expectNoA11yViolations(page, "SCR-60 Accounts with Session lost");

  // Sign in again from the banner opens the wizard for the same account.
  await page.getByRole("button", { name: "Sign in again" }).first().click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  await expect(
    page.getByRole("heading", { name: new RegExp(`^Sign in again to ${displayName}`) }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-02 sign in again");
  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page).toHaveURL(/\/accounts$/);

  await page.getByRole("button", { name: "Unlink" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText(`Unlink ${displayName}?`);
  await expectNoA11yViolations(page, "SCR-60 unlink dialog");
  await dialog.getByRole("button", { name: "Unlink account" }).click();

  await expect(page).toHaveURL(/\/inbox$/);
  await expect(connectButton(page)).toBeVisible();
  await expect(banner).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-10 Inbox after unlink");
});
