import { expect, test } from "@playwright/test";
import {
  addVirtualAuthenticator,
  codeOf,
  expectNoA11yViolations,
  NEW_SIGN_IN_SUBJECT,
  requestEmail,
  SIGN_IN_SUBJECT,
  signInByLink,
  signOut,
  signUp,
} from "../support/flows";
import {
  linkOf,
  messagesTo,
  uniqueAddress,
  waitForMail,
} from "../support/mailpit";

test("AC-34: sign up by link creates the account, offers a passkey, lands on the empty Inbox; same address again is the same account", async ({
  page,
}) => {
  await addVirtualAuthenticator(page);
  const address = uniqueAddress("Anton").replace("@", "+work@");

  await page.goto("/sign-in");
  await expect(
    page.getByRole("heading", { name: "Sign in to teleX" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-01 sign-in");

  await page.getByLabel("Email").fill(address);
  await page.getByRole("button", { name: "Email me a sign-in link" }).click();
  await expect(
    page.getByRole("heading", { name: "Check your email" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-07 check your email");

  // the sign-in email goes to the address exactly as typed
  const mail = await waitForMail(address, SIGN_IN_SUBJECT);
  await page.goto(linkOf(mail));
  await expect(
    page.getByRole("button", { name: `Continue as ${address}` }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-08 confirm sign-in");
  await page.getByRole("button", { name: /^Continue as / }).click();

  await expect(page).toHaveURL(/\/welcome\/passkey$/);
  await expect(
    page.getByRole("button", { name: "Create a passkey" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-09 create a passkey");
  await page.getByRole("button", { name: "Not now" }).click();

  await expect(page).toHaveURL(/\/inbox$/);
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Connect Telegram" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-10 Inbox");

  // same account when letter case and +tag differ: sign in again, then see the original address in Profile
  await signOut(page);
  await signInByLink(
    page,
    address.replace("Anton", "ANTON").replace("+work", "+other"),
  );
  await expect(page).toHaveURL(/\/inbox$/);
  await page.getByRole("button", { name: "Profile and security" }).click();
  await expect(page.getByText(`Signed in as ${address}`)).toBeVisible();
});

test("AC-82: the code typed on the laptop signs it in; the link from the same email then no longer works", async ({
  browser,
}) => {
  const laptop = await browser.newContext();
  const phone = await browser.newContext();
  const address = uniqueAddress("code");
  const page = await laptop.newPage();

  await requestEmail(page, address);
  const mail = await waitForMail(address, SIGN_IN_SUBJECT);
  await page.getByLabel("Digit 1").click();
  await page.keyboard.type(codeOf(mail));
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/\/(welcome\/passkey|inbox)$/);

  const other = await phone.newPage();
  await other.goto(linkOf(mail));
  await expect(
    other.getByRole("heading", { name: "This link was already used" }),
  ).toBeVisible();
  await expect(
    other.getByRole("button", { name: /^Continue as / }),
  ).toHaveCount(0);
  await expect(
    other.getByRole("heading", { name: "Sign in to teleX" }),
  ).toHaveCount(0);
  await expectNoA11yViolations(other, "SCR-08 unusable link");
  await laptop.close();
  await phone.close();
});

test("AC-98: a later sign-in sends the New sign-in email; the account-creating one does not", async ({
  page,
}) => {
  const address = await signUp(page);
  await expect(async () => {
    expect(await messagesTo(address, SIGN_IN_SUBJECT)).toHaveLength(1);
  }).toPass();
  expect(await messagesTo(address, NEW_SIGN_IN_SUBJECT)).toHaveLength(0);

  await signOut(page);
  await signInByLink(page, address, 2);
  await expect(page).toHaveURL(/\/inbox$/);

  const notice = await waitForMail(address, NEW_SIGN_IN_SUBJECT);
  expect(notice.text).toMatch(/Browser: .*Chrome/);
  expect(notice.text).toMatch(/Device type: \S+/);
  expect(notice.text).toMatch(/Time: .+\(.+\)/);
  expect(notice.text).toMatch(/UTC: .+UTC/);
  expect(notice.text).toMatch(/\/profile#sessions/);
  expect(await messagesTo(address, NEW_SIGN_IN_SUBJECT)).toHaveLength(1);
});

test("AC-95: Sign out lands on the sign-in page and Back shows none of the Owner's data", async ({
  page,
}) => {
  await signUp(page);
  await signOut(page);
  await page.goBack({ waitUntil: "load" }).catch(() => undefined);
  await expect(
    page.getByRole("button", { name: "Connect Telegram" }),
  ).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Sign out" })).toHaveCount(0);
  await page.goto("/inbox"); // even a direct visit shows no Owner data
  await expect(page).toHaveURL(/\/sign-in$/);
  await expect(
    page.getByRole("button", { name: "Connect Telegram" }),
  ).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Inbox" })).toHaveCount(0);
  expect((await page.request.get("/api/v1/me")).status()).toBe(401);
});

test("AC-101: a protected deep link leads to sign-in and back; a non-teleX destination leads to the Inbox", async ({
  page,
}) => {
  const address = await signUp(page);
  await signOut(page);

  await page.goto("/profile#sessions");
  await expect(page).toHaveURL(/\/sign-in$/);
  await signInByLink(page, address, 2);
  await expect(page).toHaveURL(/\/profile#sessions$/);
  await expect(
    page.getByRole("heading", { name: "Sign-in sessions" }),
  ).toBeVisible();

  await signOut(page);
  await page.goto("/sign-in");
  await page.evaluate(() =>
    localStorage.setItem("telex.destination", "//evil.test/x"),
  );
  await signInByLink(page, address, 3);
  await expect(page).toHaveURL(/\/inbox$/);
});
