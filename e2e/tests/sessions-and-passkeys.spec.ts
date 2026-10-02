import { expect, test } from "@playwright/test";
import {
  addVirtualAuthenticator,
  expectNoA11yViolations,
  signInByLink,
  signOut,
  signUp,
} from "../support/flows";
import { uniqueAddress } from "../support/mailpit";

test("AC-93: ending the phone's session from the laptop lands the phone on Session ended", async ({
  browser,
}) => {
  const laptopCtx = await browser.newContext();
  const phoneCtx = await browser.newContext();
  const laptop = await laptopCtx.newPage();
  const phone = await phoneCtx.newPage();

  const address = await signUp(laptop);
  await signInByLink(phone, address, 2);
  await expect(phone).toHaveURL(/\/inbox$/);

  await laptop.getByRole("button", { name: "Profile and security" }).click();
  await expect(
    laptop.getByRole("heading", { name: "Sign-in sessions" }),
  ).toBeVisible();
  const rows = laptop.locator("#sessions li");
  await expect(rows).toHaveCount(2);
  await expect(rows.filter({ hasText: "This device" })).toHaveCount(1);
  await expect(rows.first()).toContainText(/Chrome/);
  await expect(rows.first()).toContainText(
    /Computer|Phone|Tablet|Unknown device/,
  );
  await expect(rows.first()).toContainText(/Active /);
  await expectNoA11yViolations(laptop, "SCR-64 Profile and security");

  await rows
    .filter({ hasNotText: "This device" })
    .getByRole("button", { name: "End session" })
    .click();
  await expect(rows).toHaveCount(1);

  await phone.getByRole("button", { name: "Profile and security" }).click();
  await expect(phone).toHaveURL(/\/session-ended$/);
  await expect(
    phone.getByRole("heading", { name: "Session ended" }),
  ).toBeVisible();
  await expectNoA11yViolations(phone, "SCR-92 Session ended");
  await laptopCtx.close();
  await phoneCtx.close();
});

test("AC-89: create a passkey, see it in Profile, sign in with it without typing an email", async ({
  page,
  browserName,
}) => {
  test.skip(
    browserName !== "chromium",
    "CDP virtual authenticator is Chromium only",
  );
  await addVirtualAuthenticator(page);
  const address = uniqueAddress("passkey");

  await signInByLink(page, address);
  await page.getByRole("button", { name: "Create a passkey" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  await page.getByRole("button", { name: "Profile and security" }).click();
  const passkeys = page.getByRole("region", { name: "Passkeys" });
  await expect(passkeys.getByText(/Chrome/)).toBeVisible();
  await expect(passkeys.getByText(/Created /)).toBeVisible();
  await expect(passkeys.getByText("Never used")).toBeVisible();
  await expectNoA11yViolations(page, "SCR-64 with a passkey");

  await signOut(page);
  await page.getByRole("button", { name: "Sign in with a passkey" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  await page.getByRole("button", { name: "Profile and security" }).click();
  await expect(passkeys.getByText(/Last used /)).toBeVisible();
  await expect(passkeys.getByText("Never used")).toHaveCount(0);
});

test("AC-92: a removed passkey is refused at sign-in; the email sign-in is still offered", async ({
  page,
  browserName,
}) => {
  test.skip(
    browserName !== "chromium",
    "CDP virtual authenticator is Chromium only",
  );
  await addVirtualAuthenticator(page);
  const address = uniqueAddress("removed");

  await signInByLink(page, address);
  await page.getByRole("button", { name: "Create a passkey" }).click();
  await expect(page).toHaveURL(/\/inbox$/);

  await page.getByRole("button", { name: "Profile and security" }).click();
  const passkeys = page.getByRole("region", { name: "Passkeys" });
  await expect(passkeys.getByText("Never used")).toBeVisible();
  await passkeys.getByRole("button", { name: "Remove" }).click();
  await page.getByRole("button", { name: "Remove passkey" }).click();
  await expect(passkeys.getByText("No passkeys yet.")).toBeVisible();

  // the device still holds the credential, but the server no longer knows it
  await signOut(page);
  await page.getByRole("button", { name: "Sign in with a passkey" }).click();
  await expect(
    page.getByText("That passkey didn't work. Sign in with your email instead."),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/sign-in$/);
  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Email me a sign-in link" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-01 passkey refused");
});
