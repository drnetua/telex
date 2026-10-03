import { expect, test } from "@playwright/test";
import { signUp } from "../support/flows";
import {
  bannerOf,
  inboxLink,
  isPhone,
  setPulseFixture,
  SIGNAL_BUDGET_MS,
} from "../support/shell";

const OFFLINE =
  "You're offline. teleX will update when your connection is back.";
const NOT_RESPONDING = "teleX isn't responding.";
const STILL_DOWN = "Still can't reach teleX. It keeps trying on its own.";
const DISCONNECTED = "Your account is disconnected from Telegram.";
const BUDGET = "Your assistant budget is used up.";

test("AC-174: the Inbox counter follows the fixture within 5 s, with no number at 0 and 99+ above 99", async ({
  page,
}) => {
  await signUp(page);
  const inbox = inboxLink(page);
  const within = { timeout: SIGNAL_BUDGET_MS };

  await setPulseFixture(page, { inboxCount: 3, conditions: [] });
  await expect(inbox.getByLabel("3 items need you")).toBeVisible(within);

  await setPulseFixture(page, { inboxCount: 4, conditions: [] });
  await expect(inbox.getByLabel("4 items need you")).toBeVisible(within);

  await setPulseFixture(page, { inboxCount: 2, conditions: [] });
  await expect(inbox.getByLabel("2 items need you")).toBeVisible(within);

  await setPulseFixture(page, { inboxCount: 100, conditions: [] });
  await expect(inbox.getByLabel("More than 99 items need you")).toHaveText(
    "99+",
    within,
  );

  await setPulseFixture(page, { inboxCount: 0, conditions: [] });
  await expect(inbox.getByLabel(/items? need/)).toHaveCount(0, within);
  await expect(inbox).toBeVisible();
  await expect(page).toHaveURL(/\/inbox$/);
});

test("AC-174: while offline the last counter number is kept", async ({
  page,
  context,
}) => {
  await signUp(page);
  await setPulseFixture(page, { inboxCount: 3, conditions: [] });
  const inbox = inboxLink(page);
  await expect(inbox.getByLabel("3 items need you")).toBeVisible({
    timeout: SIGNAL_BUDGET_MS,
  });
  await context.setOffline(true);
  await expect(bannerOf(page, OFFLINE)).toBeVisible({
    timeout: SIGNAL_BUDGET_MS,
  });
  await expect(inbox.getByLabel("3 items need you")).toBeVisible();
  await context.setOffline(false);
  await expect(bannerOf(page, OFFLINE)).toHaveCount(0, {
    timeout: SIGNAL_BUDGET_MS,
  });
});

test("AC-175: an Owner with no waiting items sees no number while another Owner has 5", async ({
  page,
  browser,
}) => {
  await signUp(page);
  await setPulseFixture(page, { inboxCount: 5, conditions: [] });
  await expect(inboxLink(page).getByLabel("5 items need you")).toBeVisible({
    timeout: SIGNAL_BUDGET_MS,
  });

  const other = await browser.newContext({
    viewport: page.viewportSize() ?? undefined,
    baseURL: test.info().project.use.baseURL,
  });
  try {
    const second = await other.newPage();
    await signUp(second);
    // let several pulses land before asserting absence
    await second.waitForTimeout(SIGNAL_BUDGET_MS);
    await expect(inboxLink(second)).toBeVisible();
    await expect(inboxLink(second).getByLabel(/items? need/)).toHaveCount(0);
    const nav = second.getByRole("navigation", { name: "Main" });
    await expect(nav).not.toContainText(/\b5\b/);
  } finally {
    await other.close();
  }
});

test("AC-176: the offline banner appears and clears within 5 s, with an icon, words and Try again", async ({
  page,
  context,
}) => {
  await signUp(page);
  const banner = bannerOf(page, OFFLINE);
  await expect(banner).toHaveCount(0);

  await context.setOffline(true);
  await expect(banner).toBeVisible({ timeout: SIGNAL_BUDGET_MS });
  await expect(banner.locator("svg").first()).toBeVisible();
  await expect(banner.getByRole("button", { name: "Try again" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();

  await context.setOffline(false);
  await expect(banner).toHaveCount(0, { timeout: SIGNAL_BUDGET_MS });
  await expect(
    page.getByRole("heading", { name: "teleX is unavailable" }),
  ).toHaveCount(0);
});

test("AC-176: teleX not answering shows the not-responding banner within 5 s and clears when it answers", async ({
  page,
}) => {
  await signUp(page);
  const banner = bannerOf(page, NOT_RESPONDING);

  await page.route("**/api/**", (route) => route.abort());
  await expect(banner).toBeVisible({ timeout: SIGNAL_BUDGET_MS });
  await expect(banner.getByRole("button", { name: "Try again" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();

  await page.unroute("**/api/**");
  await expect(banner).toHaveCount(0, { timeout: SIGNAL_BUDGET_MS });
});

test("AC-176: a 503 from the proxy is the not-responding banner, never the full-page failure", async ({
  page,
}) => {
  await signUp(page);
  await page.route("**/api/v1/pulse", (route) =>
    route.fulfill({ status: 503, body: "" }),
  );
  await expect(bannerOf(page, NOT_RESPONDING)).toBeVisible({
    timeout: SIGNAL_BUDGET_MS,
  });
  await expect(
    page.getByRole("heading", { name: "teleX is unavailable" }),
  ).toHaveCount(0);
});

test("AC-177 / AC-178: Try again while still down says so, keeps the screen, and nothing closes the banner", async ({
  page,
  context,
}) => {
  await signUp(page);
  await context.setOffline(true);
  const banner = bannerOf(page, OFFLINE);
  await expect(banner).toBeVisible({ timeout: SIGNAL_BUDGET_MS });

  await banner.getByRole("button", { name: "Try again" }).click();
  const still = bannerOf(page, STILL_DOWN);
  await expect(still).toBeVisible({ timeout: SIGNAL_BUDGET_MS });
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
  await expect(page).toHaveURL(/\/inbox$/);

  await expect(
    still.getByRole("button", { name: /close|dismiss/i }),
  ).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(still).toBeVisible();

  await context.setOffline(false);
  await expect(still).toHaveCount(0, { timeout: SIGNAL_BUDGET_MS });
});

test("AC-178: fixture conditions alone show the most important one with +N more", async ({
  page,
}) => {
  await signUp(page);
  await setPulseFixture(page, {
    inboxCount: 0,
    conditions: ["budget-exhausted", "account-disconnected"],
  });
  const banner = bannerOf(page, DISCONNECTED);
  await expect(banner).toBeVisible({ timeout: SIGNAL_BUDGET_MS });
  await expect(banner.getByRole("link", { name: "Reconnect" })).toBeVisible();
  await expect(banner.getByRole("button", { name: "+1 more" })).toBeVisible();

  await setPulseFixture(page, { inboxCount: 0, conditions: [] });
  await expect(banner).toHaveCount(0, { timeout: SIGNAL_BUDGET_MS });
});

test("AC-178: offline outranks fixture conditions, and +2 more lists the others with their actions", async ({
  page,
  context,
}) => {
  await signUp(page);
  await setPulseFixture(page, {
    inboxCount: 0,
    conditions: ["account-disconnected", "budget-exhausted"],
  });
  await expect(bannerOf(page, DISCONNECTED)).toBeVisible({
    timeout: SIGNAL_BUDGET_MS,
  });

  await context.setOffline(true);
  const banner = bannerOf(page, OFFLINE);
  await expect(banner).toBeVisible({ timeout: SIGNAL_BUDGET_MS });
  await expect(banner.getByText(DISCONNECTED)).toHaveCount(0);

  await banner.getByRole("button", { name: "+2 more" }).click();
  const list = banner.getByRole("list");
  await expect(list.getByText(DISCONNECTED)).toBeVisible();
  await expect(list.getByText(BUDGET)).toBeVisible();
  await expect(list.getByRole("link", { name: "Reconnect" })).toBeVisible();
  await expect(list.getByRole("link", { name: "Open budget" })).toBeVisible();
  await expect(
    banner.getByRole("button", { name: /close|dismiss/i }),
  ).toHaveCount(0);
  if (isPhone(page)) {
    const overflow = await banner.evaluate(
      (el) => el.scrollWidth - el.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  }
});
