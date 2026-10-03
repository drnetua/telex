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

test("AC-102: a server failure shows teleX is unavailable and Retry repeats the action", async ({
  page,
}) => {
  await signUp(page);
  let failing = true;
  await page.route("**/api/v1/passkeys", (route) =>
    failing ? route.fulfill({ status: 503, body: "" }) : route.continue(),
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

test("AC-102: no answer within 10 seconds shows teleX is unavailable", async ({
  page,
}) => {
  test.setTimeout(90_000);
  await signUp(page);
  await page.route("**/api/v1/passkeys", () => new Promise(() => undefined));
  await openProfile(page);
  await expect(
    page.getByRole("heading", { name: "teleX is unavailable" }),
  ).toBeVisible({ timeout: 20_000 });
});
