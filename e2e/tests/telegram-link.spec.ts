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

const connectButton = (page: Page) =>
  page.getByRole("button", { name: "Connect Telegram" });
const accountLine = (page: Page, name: string) =>
  page.getByRole("link", { name: new RegExp(name) });

test("AC-01: link an account with two-step verification, see it syncing in the Inbox", async ({
  page,
}) => {
  const { digits, displayName, chats } = testNumber(SCENARIO.twoStep);
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
  // The sync finishes at the account's own chat count ("x of N chats" is still syncing).
  await expect(line).toContainText(new RegExp(`(?<!of )${chats} chats?`));
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
  await expect(page.getByRole("alert")).toContainText(
    "This code has expired. Send a new code.",
  );
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
    .getByText(
      /Telegram asks you to wait\. You can try again at \d\d:\d\d, in \d+:\d\d\./,
    )
    .first();
  await expect(
    page.getByRole("heading", { name: "Too many attempts" }),
  ).toBeVisible();
  await expect(wait).toBeVisible();
  const first = await wait.textContent();
  await expect
    .poll(async () => wait.textContent(), {
      message: "the countdown ticks",
      timeout: 5_000,
    })
    .not.toBe(first);
  await expectNoA11yViolations(page, "SCR-02 wait countdown");
});

test("AC-02: starting again with the same number before the wait is over shows the remaining wait, not a code step", async ({
  page,
}) => {
  const { digits } = testNumber(SCENARIO.floodOnPhone);
  const wait = page
    .getByText(
      /Telegram asks you to wait\. You can try again at \d\d:\d\d, in \d+:\d\d\./,
    )
    .first();
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expect(
    page.getByRole("heading", { name: "Too many attempts" }),
  ).toBeVisible();
  await expect(wait).toBeVisible();

  await page.getByRole("button", { name: "Back" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await startAndSendPhone(page, digits);
  await expect(
    page.getByRole("heading", { name: "Too many attempts" }),
  ).toBeVisible();
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
  // One Status Banner (C-04) in the shell, fed by the pulse, not a second one in the page.
  await expect(
    page.getByRole("status").filter({ hasText: "is disconnected" }),
  ).toHaveCount(1);
  await expectNoA11yViolations(
    page,
    "SCR-10 Inbox with Session lost and banner",
  );

  await line.click();
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(page.getByText("Session lost")).toBeVisible();
  await expect(banner).toBeVisible();
  await expectNoA11yViolations(page, "SCR-60 Accounts with Session lost");

  // Sign in again from the banner opens the wizard for the same account.
  await page.getByRole("button", { name: "Sign in again" }).first().click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  await expect(
    page.getByRole("heading", {
      name: new RegExp(`^Sign in again to ${displayName}`),
    }),
  ).toBeVisible();
  // AC-122: SCR-02 shows the same banner above the card.
  await expect(banner).toBeVisible();
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

const chatCount = (chats: number) => new RegExp(`(?<!of )${chats} chats?`);

test("AC-117, AC-122: signing in again with the same account brings it back Connected and the banner goes away", async ({
  page,
}) => {
  // The first session ends a moment after the link; the one made by signing in again survives.
  const { digits, displayName, chats } = testNumber(SCENARIO.terminateOnce);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  const banner = page.getByText(`${displayName}'s Telegram is disconnected.`);
  await expect(accountLine(page, displayName)).toContainText("Session lost", {
    timeout: 30_000,
  });
  await expect(banner).toBeVisible();

  await page.getByRole("button", { name: "Sign in again" }).first().click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  await expect(
    page.getByRole("heading", {
      name: new RegExp(`^Sign in again to ${displayName}`),
    }),
  ).toBeVisible();
  await page.getByLabel("Phone number").fill(`+${digits}`);
  await page.getByRole("button", { name: "Send code" }).click();
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();

  // The banner's action starts the wizard from Accounts, so that is where it ends: same account, connected again.
  await expect(page).toHaveURL(/\/accounts$/);
  await expect(
    page.getByText(`${displayName} is connected again.`),
  ).toBeVisible();
  const row = page.locator(".list-group-item", { hasText: displayName });
  await expect(row).toContainText("Connected");
  await expect(row).not.toContainText("Session lost");
  await expect(row).toContainText(chatCount(chats));
  await expect(row.getByRole("button", { name: "Sign in again" })).toHaveCount(
    0,
  );
  await expect(banner).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-60 Accounts after signing in again");

  // The new session stays up (the fake ends only the first one) and the banner is gone on the Inbox too.
  await page.waitForTimeout(4_000);
  await page.goto("/inbox");
  const line = accountLine(page, displayName);
  await expect(line).toContainText("Connected");
  await expect(line).toContainText(chatCount(chats));
  await expect(banner).toHaveCount(0);
  await expect(connectButton(page)).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-10 Inbox after signing in again");
});

test("AC-122: while Telegram is unreachable the account shows Reconnecting on Accounts, then Connected, with no banner", async ({
  page,
}) => {
  // The fake makes Telegram unreachable a moment after the link, then reachable again.
  const { digits, displayName } = testNumber(SCENARIO.outage);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  await page.goto("/accounts");
  const row = page.locator(".list-group-item", { hasText: displayName });
  const disconnected = page.getByText(
    `${displayName}'s Telegram is disconnected.`,
  );
  await expect(row).toContainText("Reconnecting", { timeout: 30_000 });
  await expect(
    page.getByText(
      "Telegram can't be reached right now. teleX reconnects by itself.",
    ),
  ).toBeVisible();
  await expect(disconnected).toHaveCount(0);
  await expect(row.getByRole("button", { name: "Sign in again" })).toHaveCount(
    0,
  );
  await expectNoA11yViolations(page, "SCR-60 Accounts while Reconnecting");

  await expect(row).toContainText("Connected", { timeout: 30_000 });
  await expect(row).not.toContainText("Reconnecting");
  await expect(disconnected).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-60 Accounts reconnected");
});

test("AC-106: a wrong two-step password shows the hint and the reset note, and can be retried", async ({
  page,
}) => {
  const { digits, displayName } = testNumber(SCENARIO.twoStep);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByText("Hint: first pet")).toBeVisible();

  await page.getByLabel("Password").fill("not-the-password");
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "That password is not right.",
  );
  await expect(page.getByText("Hint: first pet")).toBeVisible();
  await expect(
    page.getByText(
      "Forgot your password? It can only be reset in the Telegram app.",
    ),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-02 wrong password");

  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
  await expect(accountLine(page, displayName)).toBeVisible();
});

test("AC-107: an invalid, unregistered or banned phone number blocks the step and says which", async ({
  page,
}) => {
  await signUp(page);
  await connectButton(page).click();
  await expect(page).toHaveURL(/\/connect-telegram$/);
  const phone = page.getByLabel("Phone number");
  const send = page.getByRole("button", { name: "Send code" });
  const noCodeStep = page.getByRole("group", { name: "Login code" });

  await phone.fill("+123");
  await send.click();
  await expect(page.getByRole("alert")).toContainText(
    "This isn't a valid phone number. Check the country code and the digits.",
  );
  await expect(noCodeStep).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 invalid phone");

  await phone.fill(`+${testNumber(SCENARIO.unregistered).digits}`);
  await send.click();
  await expect(page.getByRole("alert")).toContainText(
    "No Telegram account uses this number. Create the account in the Telegram app first, then come back.",
  );
  await expect(noCodeStep).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 unregistered phone");

  await phone.fill(`+${testNumber(SCENARIO.banned).digits}`);
  await send.click();
  await expect(page.getByRole("alert")).toContainText(
    "Telegram has banned this number, so it can't be linked.",
  );
  await expect(noCodeStep).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 banned phone");
});

test("AC-107: a number Telegram finds unregistered once the code is checked ends the attempt and says why", async ({
  page,
}) => {
  const { digits } = testNumber(SCENARIO.unregisteredAfterCode);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await typeCode(page, CODE);
  await page.getByRole("button", { name: "Continue" }).click();

  const heading = page.getByRole("heading", { name: "This linking has ended" });
  await expect(heading).toBeVisible();
  await expect(heading).toBeFocused();
  await expect(
    page.getByText(
      "No Telegram account uses this number. Create the account in the Telegram app first, then come back.",
    ),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Start again" })).toBeVisible();
  await expect(page.getByRole("group", { name: "Login code" })).toHaveCount(0);
  await expectNoA11yViolations(page, "SCR-02 ended after a code refusal");
});

test("AC-107: a refused Send a new code ends the attempt and says why", async ({
  page,
}) => {
  const { digits } = testNumber(SCENARIO.resendInvalid);
  await signUp(page);
  await startAndSendPhone(page, digits);
  await expectCodeStep(page);
  await page.getByRole("button", { name: "Send a new code" }).click();

  const heading = page.getByRole("heading", { name: "This linking has ended" });
  await expect(heading).toBeVisible();
  await expect(heading).toBeFocused();
  await expect(
    page.getByText(
      "This isn't a valid phone number. Check the country code and the digits.",
    ),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-02 ended after a resend refusal");
});

test.describe("an installation without Telegram app credentials", () => {
  // The second app of the stack, started without TELEX_TELEGRAM_API_ID / _API_HASH (compose profile `unconfigured`).
  test.use({
    baseURL: process.env.TELEX_UNCONFIGURED_URL ?? "http://localhost:8081",
  });

  test("AC-119: Connect Telegram says linking isn't set up and does not open the wizard", async ({
    page,
  }) => {
    await signUp(page);
    await connectButton(page).click();
    await expect(
      page.getByText(
        "Telegram linking isn't set up on this installation yet. The person who runs teleX has to finish the setup.",
      ),
    ).toBeVisible();
    await expect(page).toHaveURL(/\/inbox$/);
    await expect(
      page.getByRole("heading", { name: "Connect your Telegram" }),
    ).toHaveCount(0);
    await expect(page.getByLabel("Phone number")).toHaveCount(0);
    await expectNoA11yViolations(
      page,
      "SCR-10 Inbox with the not-set-up Toast",
    );
  });
});
