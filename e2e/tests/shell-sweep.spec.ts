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
      conditions: ["bot-blocked", "budget-exhausted"],
    });
    for (const screen of shellScreens) {
      await page.goto(screen.path);
      await expect(
        page.getByText("The teleX bot is blocked in Telegram."),
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

test("NFR: the Inbox and its counter are usable within 2.5 s on fast 4G (p75 of cold opens)", async ({
  page,
}, testInfo) => {
  await signUp(page);
  await setPulseFixture(page, { inboxCount: 7, conditions: [] });

  // Measured in the page: performance.now() counts from navigation start, so Playwright's own round trips stay out.
  await page.addInitScript(() => {
    const w = window as unknown as { __counterAt?: number };
    new MutationObserver(() => {
      if (
        w.__counterAt === undefined &&
        document.querySelector('[aria-label="7 items need you"]')
      )
        w.__counterAt = performance.now();
    }).observe(document, { subtree: true, childList: true, attributes: true });
  });

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

  // cold opens, a fresh cache each time (the cache is disabled); the test plan asks for p75, here of five opens
  const samples: number[] = [];
  for (let i = 0; i < 5; i++) {
    await page.goto("/inbox", { waitUntil: "commit" });
    await page.waitForFunction(
      () => (window as unknown as { __counterAt?: number }).__counterAt,
      undefined,
      { timeout: 15_000 },
    );
    await expect(
      page.getByRole("button", { name: "Connect Telegram" }),
    ).toBeEnabled();
    samples.push(
      await page.evaluate(
        () => (window as unknown as { __counterAt: number }).__counterAt,
      ),
    );
  }
  const sorted = [...samples].sort((a, b) => a - b);
  const p75 = sorted[Math.ceil(sorted.length * 0.75) - 1]!;
  testInfo.annotations.push({
    type: "inbox-counter-ms",
    description: `p75 ${Math.round(p75)}; samples ${samples.map(Math.round).join(", ")}`,
  });
  expect(p75, "navigation start to the counter's first render").toBeLessThan(
    2_500,
  );
});
