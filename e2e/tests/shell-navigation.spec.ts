import { expect, test } from "@playwright/test";
import {
  codeOf,
  expectNoA11yViolations,
  requestEmail,
  SIGN_IN_SUBJECT,
  signInByLink,
  signUp,
} from "../support/flows";
import { linkOf, uniqueAddress, waitForMail } from "../support/mailpit";
import {
  isPhone,
  newDevice,
  openMore,
  openProfile,
  sectionList,
  setPulseFixture,
  signOutFromShell,
  unbuiltSections,
} from "../support/shell";

const ORDER = [
  "Overview",
  "Inbox",
  "Chats",
  "Assistants",
  "Runs",
  "Tasks",
  "Settings",
];

test("AC-170 / AC-43: the Inbox opens first; side menu at 768 px and up, bottom bar with More below it", async ({
  page,
}) => {
  await signUp(page);
  await expect(page).toHaveURL(/\/inbox$/);
  await setPulseFixture(page, { inboxCount: 3, conditions: [] });
  const nav = page.getByRole("navigation", { name: "Main" });

  if (!isPhone(page)) {
    // AC-170: seven sections in app-map order, current one marked by more than colour
    await expect(nav.getByRole("link")).toHaveText(
      ORDER.map((name) => new RegExp(`^${name}`)),
    );
    const inbox = nav.getByRole("link", { name: /^Inbox/ });
    await expect(inbox).toHaveAttribute("aria-current", "page");
    await expect(inbox.locator(".fw-bold")).toHaveText("Inbox");
    await expect(nav.getByRole("link", { name: /^Chats/ })).not.toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(inbox.getByLabel("3 items need you")).toBeVisible();
    await expectNoA11yViolations(page, "side menu");
    return;
  }

  // AC-43: at most five items, the Inbox with its counter always in the bar
  const items = nav.getByRole("link").or(nav.getByRole("button"));
  expect(await items.count()).toBeLessThanOrEqual(5);
  const inbox = nav.getByRole("link", { name: /^Inbox/ });
  await expect(inbox).toBeVisible();
  await expect(inbox.getByLabel("3 items need you")).toBeVisible();
  for (const s of sectionList.filter((x) => x.phone === "bar")) {
    await expect(nav.getByRole("link", { name: s.name })).toBeVisible();
  }

  // the sections that don't fit are one tap away under More; the Inbox never moves there
  const sheet = await openMore(page);
  for (const s of sectionList.filter((x) => x.phone === "more")) {
    await expect(sheet.getByRole("link", { name: s.name })).toBeVisible();
  }
  await expect(sheet.getByRole("link", { name: /Inbox/ })).toHaveCount(0);
  await expectNoA11yViolations(page, "More sheet");

  // a More section open: More is marked current and the Inbox is still in the bar
  await sheet.getByRole("link", { name: "Runs" }).click();
  await expect(page).toHaveURL(/\/runs$/);
  await expect(nav.getByRole("button", { name: "More" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  await expect(nav.getByRole("link", { name: /^Inbox/ })).toBeVisible();
});

for (const section of unbuiltSections) {
  test(`AC-171: ${section.name} shows Coming soon with a way back to the Inbox`, async ({
    page,
  }) => {
    await signUp(page);
    await page.goto(section.path);
    await expect(
      page.getByRole("heading", { name: section.name, level: 1 }),
    ).toBeVisible();
    await expect(page.getByText("Coming soon")).toBeVisible();
    await expectNoA11yViolations(page, `${section.name} coming soon`);

    // the navigation stays in place with the section marked as current
    const nav = page.getByRole("navigation", { name: "Main" });
    await expect(nav).toBeVisible();
    const marked =
      isPhone(page) && section.phone === "more"
        ? nav.getByRole("button", { name: "More" })
        : nav.getByRole("link", { name: new RegExp(`^${section.name}`) });
    await expect(marked).toHaveAttribute("aria-current", "page");

    await page.getByRole("link", { name: "Go to Inbox" }).click();
    await expect(page).toHaveURL(/\/inbox$/);
  });
}

test("AC-172: Settings lists Profile and security one step away; Sign out ends the session from the shell", async ({
  page,
}) => {
  await signUp(page);
  await page.goto("/settings");
  await expect(
    page.getByRole("heading", { name: "Settings", level: 1 }),
  ).toBeVisible();
  const entry = page.getByRole("link", { name: /Profile and security/ });
  await expect(entry).toHaveCount(1);
  await expectNoA11yViolations(page, "Settings");

  await entry.click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(
    page.getByRole("heading", { name: "Profile and security", level: 1 }),
  ).toBeVisible();

  // Sign out is reachable from the shell on a screen other than the Inbox too
  await signOutFromShell(page);
  await expect(page.getByLabel("Email")).toBeVisible();

  // the Sign-in Session is over: a section link no longer opens the shell
  await page.goto("/inbox");
  await expect(page).toHaveURL(/\/sign-in$/);
});

test("AC-173: a signed-out link shows only sign-in, and signing up lands on that section after the passkey offer", async ({
  page,
}) => {
  await page.goto("/runs");
  await expect(page).toHaveURL(/\/sign-in$/);
  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Main" })).toHaveCount(0);
  await expect(page.getByRole("status").filter({ hasText: /\S/ })).toHaveCount(
    0,
  );
  await expect(page.getByLabel(/items? need/)).toHaveCount(0);

  // sign up in this same browser: passkey offer first, then the page the link pointed to
  const address = uniqueAddress("return");
  await signInByLink(page, address);
  await expect(page).toHaveURL(/\/welcome\/passkey$/);
  await page.getByRole("button", { name: /^(Not now|Continue)$/ }).click();
  await expect(page).toHaveURL(/\/runs$/);
  await expect(
    page.getByRole("heading", { name: "Runs", level: 1 }),
  ).toBeVisible();
});

test("AC-173: a visitor who never signed in sees the sign-in page for every section, a coming-soon one included", async ({
  page,
}) => {
  for (const s of sectionList) {
    await page.goto(s.path);
    await expect(page).toHaveURL(/\/sign-in$/);
    await expect(page.getByRole("navigation", { name: "Main" })).toHaveCount(0);
  }
});

test("AC-173 / AC-07b: a session ended elsewhere shows Session ended with nothing of the shell", async ({
  browser,
}, testInfo) => {
  const viewport = testInfo.project.use.viewport ?? undefined;
  const firstCtx = await browser.newContext({ viewport });
  const secondCtx = await browser.newContext({ viewport });
  const first = await firstCtx.newPage();
  const second = await secondCtx.newPage();

  const address = await signUp(first);
  await signInByLink(second, address, 2);
  await expect(second).toHaveURL(/\/inbox$/);

  await openProfile(first);
  const rows = first.locator("#sessions li");
  await expect(rows).toHaveCount(2);
  await rows
    .filter({ hasNotText: "This device" })
    .getByRole("button", { name: "End session" })
    .click();
  await expect(rows).toHaveCount(1);

  // the next action in the stopped tab: open another section
  const nav = second.getByRole("navigation", { name: "Main" });
  // (the shell's pulse may already have dropped the tab; either way it lands on Session ended)
  await nav
    .getByRole("link", { name: /^Chats/ })
    .click({ timeout: 3_000 })
    .catch(() => undefined);
  await expect(second).toHaveURL(/\/session-ended$/);
  await expect(
    second.getByRole("heading", { name: "Session ended" }),
  ).toBeVisible();
  await expect(second.getByRole("navigation", { name: "Main" })).toHaveCount(0);
  await expectNoA11yViolations(second, "SCR-92 Session ended");

  await firstCtx.close();
  await secondCtx.close();
});

test("AC-173: a session that ended while on Runs shows Session ended, and signing in again returns to Runs", async ({
  browser,
}, testInfo) => {
  const viewport = testInfo.project.use.viewport ?? undefined;
  const firstCtx = await browser.newContext({ viewport });
  const secondCtx = await browser.newContext({ viewport });
  const first = await firstCtx.newPage();
  const second = await secondCtx.newPage();

  const address = await signUp(first);
  await signInByLink(second, address, 2);
  await expect(second).toHaveURL(/\/inbox$/);
  await second.goto("/runs");
  await expect(
    second.getByRole("heading", { name: "Runs", level: 1 }),
  ).toBeVisible();

  await openProfile(first);
  const rows = first.locator("#sessions li");
  await expect(rows).toHaveCount(2);
  await rows
    .filter({ hasNotText: "This device" })
    .getByRole("button", { name: "End session" })
    .click();
  await expect(rows).toHaveCount(1);

  // the stopped tab is not touched: the shell's own pulse finds out and shows Session ended
  await expect(second).toHaveURL(/\/session-ended$/, { timeout: 15_000 });
  await expect(
    second.getByRole("heading", { name: "Session ended" }),
  ).toBeVisible();
  await second.getByRole("link", { name: "Sign in again" }).click();
  await expect(second).toHaveURL(/\/sign-in$/);

  await signInByLink(second, address, 3);
  await expect(second).toHaveURL(/\/runs$/);
  await expect(
    second.getByRole("heading", { name: "Runs", level: 1 }),
  ).toBeVisible();

  await firstCtx.close();
  await secondCtx.close();
});

test("AC-173: a link opened in another browser lands on the Inbox, not on the section it was asked for in the first", async ({
  page,
  browser,
}) => {
  await page.goto("/runs");
  await expect(page).toHaveURL(/\/sign-in$/);
  const address = uniqueAddress("elsewhere");
  await requestEmail(page, address);
  const mail = await waitForMail(address, SIGN_IN_SUBJECT);

  const other = await newDevice(browser, page);
  await other.page.goto(linkOf(mail));
  await other.page.getByRole("button", { name: /^Continue as / }).click();
  await expect(other.page).toHaveURL(/\/welcome\/passkey$/);
  await other.page
    .getByRole("button", { name: /^(Not now|Continue)$/ })
    .click();
  await expect(other.page).toHaveURL(/\/inbox$/);
  await expect(
    other.page.getByRole("heading", { name: "Inbox" }),
  ).toBeVisible();
  await other.context.close();
});

test("AC-173: the Sign-in Code path also returns to the remembered section", async ({
  page,
}) => {
  await page.goto("/tasks");
  await expect(page).toHaveURL(/\/sign-in$/);
  const address = uniqueAddress("code");
  await requestEmail(page, address);
  const mail = await waitForMail(address, SIGN_IN_SUBJECT);
  await page.getByLabel("Digit 1").click();
  await page.keyboard.type(codeOf(mail));
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/\/welcome\/passkey$/);
  await page.getByRole("button", { name: /^(Not now|Continue)$/ }).click();
  await expect(page).toHaveURL(/\/tasks$/);
});
