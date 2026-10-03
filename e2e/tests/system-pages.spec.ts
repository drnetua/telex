import { expect, test } from "@playwright/test";
import { openProfile } from "../support/shell";
import { expectNoA11yViolations, signUp } from "../support/flows";

test("AC-102: an unknown address shows Page not found with Go to Inbox", async ({
  page,
}) => {
  await signUp(page);
  await page.goto("/no/such/page");
  await expect(
    page.getByRole("heading", { name: "Page not found" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-91 Page not found");
  await page.getByRole("link", { name: "Go to Inbox" }).click();
  await expect(page).toHaveURL(/\/inbox$/);
});

test("AC-102 (narrowed): an answered 500 shows teleX is unavailable and Retry repeats the action", async ({
  page,
}) => {
  await signUp(page);
  let failing = true;
  await page.route("**/api/v1/passkeys", (route) =>
    failing ? route.fulfill({ status: 500, body: "" }) : route.continue(),
  );
  await openProfile(page);
  await expect(
    page.getByRole("heading", { name: "teleX is unavailable" }),
  ).toBeVisible();
  await expectNoA11yViolations(page, "SCR-93 teleX is unavailable");

  failing = false;
  await page.getByRole("button", { name: "Retry" }).click();
  await expect(
    page.getByRole("heading", { name: "Passkeys", exact: true }),
  ).toBeVisible();
});

test("AC-102 (narrowed): an action with no answer in 10 seconds keeps the screen and shows the not-responding banner", async ({
  page,
}) => {
  test.setTimeout(90_000);
  await signUp(page);
  // the pulse hangs too: a healthy pulse would clear the banner within 3 s and make the assertion a race
  await page.route("**/api/**", () => new Promise(() => undefined));
  await openProfile(page);
  const banner = page
    .getByRole("status")
    .filter({ hasText: "teleX isn't responding." });
  await expect(banner).toBeVisible({ timeout: 20_000 });
  // nothing answers, so it stays: longer than one pulse interval
  await page.waitForTimeout(4_000);
  await expect(banner).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "teleX is unavailable" }),
  ).toHaveCount(0);
  await expect(page).toHaveURL(/\/profile$/);
});
