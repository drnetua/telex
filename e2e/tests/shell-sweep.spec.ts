import { expect, test, type Page } from "@playwright/test";
import { expectNoA11yViolations, signUp } from "../support/flows";
import {
  expectNoSidewaysScroll,
  isPhone,
  openMore,
  setPulseFixture,
  setTheme,
  shellScreens,
  type Theme,
} from "../support/shell";

const themes: Theme[] = ["light", "dark"];

async function sweep(page: Page, label: string): Promise<void> {
  for (const screen of shellScreens) {
    await page.goto(screen.path);
    await expect(
      page.getByRole("heading", { level: 1, name: screen.name }),
    ).toBeVisible();
    const state = `${screen.name} (${label})`;
    await expectNoA11yViolations(page, state);
    await expectNoSidewaysScroll(page, state);
  }
  if (isPhone(page)) {
    await page.goto("/inbox");
    await openMore(page);
    await expectNoA11yViolations(page, `More (${label})`);
    await expectNoSidewaysScroll(page, `More (${label})`);
  }
}

for (const theme of themes) {
  test(`AC-07b: every shell screen has no axe violations and no sideways scroll, ${theme} theme`, async ({
    page,
  }) => {
    test.setTimeout(180_000);
    await signUp(page);
    await setTheme(page, theme);
    await expect(page.locator("html")).toHaveAttribute("data-bs-theme", theme);
    await sweep(page, `${theme}, no banner`);
  });

  test(`AC-07b: every shell screen with Status Banners showing, ${theme} theme`, async ({
    page,
  }) => {
    test.setTimeout(180_000);
    await signUp(page);
    await setTheme(page, theme);
    await setPulseFixture(page, {
      inboxCount: 120,
      conditions: ["account-disconnected", "budget-exhausted"],
    });
    for (const screen of shellScreens) {
      await page.goto(screen.path);
      await expect(
        page.getByText("Your account is disconnected from Telegram."),
      ).toBeVisible();
      await expect(page.getByRole("button", { name: "+1 more" })).toBeVisible();
      await expect(
        page.getByRole("heading", { level: 1, name: screen.name }),
      ).toBeVisible();
      const state = `${screen.name} with a banner (${theme})`;
      await expectNoA11yViolations(page, state);
      await expectNoSidewaysScroll(page, state);
    }
    // the expanded list is a state of the banner too
    await page.getByRole("button", { name: "+1 more" }).click();
    await expect(
      page.getByText("Your assistant budget is used up."),
    ).toBeVisible();
    await expectNoA11yViolations(page, `expanded banner (${theme})`);
    await expectNoSidewaysScroll(page, `expanded banner (${theme})`);
  });
}

test("NFR: the Inbox and its counter are usable within 2.5 s on fast 4G", async ({
  page,
}, testInfo) => {
  await signUp(page);
  await setPulseFixture(page, { inboxCount: 7, conditions: [] });

  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Network.enable");
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false,
    // fast 4G, as in Lighthouse's mobile preset
    latency: 150,
    downloadThroughput: (1.6 * 1024 * 1024) / 8,
    uploadThroughput: (750 * 1024) / 8,
  });
  await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });

  const started = Date.now();
  await page.goto("/inbox", { waitUntil: "commit" });
  const inbox = page
    .getByRole("navigation", { name: "Main" })
    .getByRole("link", { name: /^Inbox/ });
  await expect(
    page.getByRole("heading", { name: "Inbox", level: 1 }),
  ).toBeVisible();
  await expect(inbox.getByLabel("7 items need you")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Connect Telegram" }),
  ).toBeEnabled();
  const elapsed = Date.now() - started;
  testInfo.annotations.push({
    type: "inbox-usable-ms",
    description: `${elapsed}`,
  });
  expect(elapsed, "Inbox + counter interactive on fast 4G").toBeLessThanOrEqual(
    2_500,
  );
});
