import { expect, test, type Page, type Route } from "@playwright/test";
import { signInByLink, signUp } from "../support/flows";
import { apiCall, getMe, newDevice, openProfile } from "../support/shell";

const themeAttr = (page: Page) =>
  page.evaluate(() => document.documentElement.getAttribute("data-bs-theme"));

const themeGroup = (page: Page) =>
  page.getByRole("radiogroup", { name: "Theme" });

const isPreferencesPatch = (r: {
  url(): string;
  request(): { method(): string };
}) =>
  r.url().endsWith("/api/v1/me/preferences") &&
  r.request().method() === "PATCH";

/** Chooses a theme on Profile and security and waits for the save to answer. */
async function chooseTheme(page: Page, label: "Light" | "Dark" | "System") {
  const saved = page.waitForResponse(isPreferencesPatch);
  await themeGroup(page).getByText(label, { exact: true }).click();
  expect((await saved).status()).toBeLessThan(300);
}

/** Records every value `data-bs-theme` takes on every document this page loads, from the first parse on. */
async function recordThemes(page: Page) {
  await page.addInitScript(() => {
    const seen: string[] = [];
    (window as unknown as { __themes: string[] }).__themes = seen;
    const note = () => {
      const v = document.documentElement?.getAttribute("data-bs-theme");
      if (v && seen[seen.length - 1] !== v) seen.push(v);
    };
    // the init script runs before the root element exists: watch the document until it does
    new MutationObserver(note).observe(document, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["data-bs-theme"],
    });
    note();
    // the theme at the moment the signed-in shell first exists in the document
    const w = window as unknown as { __themeAtShell?: string };
    new MutationObserver(() => {
      if (w.__themeAtShell === undefined && document.querySelector("nav"))
        w.__themeAtShell =
          document.documentElement.getAttribute("data-bs-theme") ?? "";
    }).observe(document, { subtree: true, childList: true });
  });
}

const themeAtShell = (page: Page) =>
  page.evaluate(
    () => (window as unknown as { __themeAtShell?: string }).__themeAtShell,
  );

const recorded = (page: Page) =>
  page.evaluate(() => (window as unknown as { __themes: string[] }).__themes);

test("AC-179: choosing Dark applies within 200 ms, without a reload, and is saved to the account", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "light" });
  await signUp(page);
  await openProfile(page);
  await chooseTheme(page, "Light");
  expect(await themeAttr(page)).toBe("light");

  await page.evaluate(() => {
    const w = window as unknown as Record<string, unknown>;
    w.__reloadMarker = true;
    w.__click = 0;
    w.__applied = 0;
    document.addEventListener("click", () => (w.__click = performance.now()), {
      capture: true,
    });
    new MutationObserver(() => {
      if (
        document.documentElement.getAttribute("data-bs-theme") === "dark" &&
        !w.__applied
      )
        w.__applied = performance.now();
    }).observe(document.documentElement, { attributes: true });
  });
  const navigations: string[] = [];
  page.on("framenavigated", (f) => navigations.push(f.url()));

  await chooseTheme(page, "Dark");
  const { click, applied, marker } = await page.evaluate(() => {
    const w = window as unknown as Record<string, number | boolean>;
    return {
      click: w.__click as number,
      applied: w.__applied as number,
      marker: w.__reloadMarker,
    };
  });
  expect(applied, "the attribute changed").toBeGreaterThan(0);
  expect(applied - click, "ms from click to data-bs-theme").toBeLessThanOrEqual(
    200,
  );
  expect(marker, "no reload happened").toBe(true);
  expect(navigations, "no navigation").toEqual([]);
  expect((await getMe(page)).theme).toBe("dark");

  // saved to the account: a reload opens dark from the first parse, never light first
  await recordThemes(page);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Profile and security", level: 1 }),
  ).toBeVisible();
  expect(await recorded(page), "every theme the reloaded page showed").toEqual([
    "dark",
  ]);
});

test("AC-180: on System the theme follows the device between light and dark without a reload", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "light" });
  await signUp(page);
  await openProfile(page);
  await chooseTheme(page, "Dark");
  await chooseTheme(page, "System");
  expect((await getMe(page)).theme).toBe("system");
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "light");
  const systemRadio = themeGroup(page).getByRole("radio", { name: "System" });
  await expect(systemRadio).toBeChecked();

  await page.evaluate(() => {
    (window as unknown as Record<string, unknown>).__reloadMarker = true;
  });
  await page.emulateMedia({ colorScheme: "dark" });
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "dark");
  // following the device doesn't turn System into Dark: the choice stays System, here and on the account
  await expect(systemRadio).toBeChecked();
  expect((await getMe(page)).theme).toBe("system");
  await page.emulateMedia({ colorScheme: "light" });
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "light");
  await expect(systemRadio).toBeChecked();
  expect((await getMe(page)).theme).toBe("system");
  expect(
    await page.evaluate(
      () => (window as unknown as Record<string, unknown>).__reloadMarker,
    ),
    "no reload happened",
  ).toBe(true);
});

test("AC-181: a first sign-in on another device shows the account's dark theme from the first signed-in screen", async ({
  page,
  browser,
}) => {
  await page.emulateMedia({ colorScheme: "light" });
  const address = await signUp(page);
  await openProfile(page);
  await chooseTheme(page, "Dark");

  const { context, page: phone } = await newDevice(browser, page, {
    colorScheme: "light",
  });
  await recordThemes(phone);
  await signInByLink(phone, address, 2);
  await expect(phone.getByRole("heading", { name: "Inbox" })).toBeVisible();
  expect(
    await themeAtShell(phone),
    "theme when the first signed-in screen appeared",
  ).toBe("dark");
  expect(await themeAttr(phone)).toBe("dark");
  await context.close();
});

test("AC-181: a device used before shows its remembered theme at first paint and switches once to the account's", async ({
  page,
  browser,
}) => {
  await page.emulateMedia({ colorScheme: "dark" });
  const address = await signUp(page);
  await openProfile(page);
  await chooseTheme(page, "Light");

  const { context, page: other } = await newDevice(browser, page);
  await signInByLink(other, address, 2);
  await openProfile(other);
  await chooseTheme(other, "Dark");
  expect((await getMe(other)).theme).toBe("dark");

  // the device that was already open picks the change up only when teleX is next opened or reloaded
  await page.waitForTimeout(1_000);
  expect(await themeAttr(page)).toBe("light");

  await recordThemes(page);
  await page.reload();
  await expect.poll(() => recorded(page)).toEqual(["light", "dark"]);
  await page.waitForTimeout(1_000);
  expect(await recorded(page), "at most one switch").toEqual(["light", "dark"]);
  await context.close();
});

test("AC-182: a theme that can't be saved reverts with a Try again toast, and Try again saves it", async ({
  page,
}) => {
  await page.emulateMedia({ colorScheme: "light" });
  await signUp(page);
  await openProfile(page);
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "light");

  const abort = (route: Route) =>
    route.request().method() === "PATCH"
      ? route.abort("connectionfailed")
      : route.continue();
  await page.route("**/api/v1/me/preferences", abort);

  await themeGroup(page).getByText("Dark", { exact: true }).click();
  const toast = page.getByRole("alert").filter({
    hasText: "Your theme wasn't saved.",
  });
  await expect(toast).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "light");
  await expect(
    themeGroup(page).getByRole("radio", { name: "System" }),
  ).toBeChecked();
  expect((await getMe(page)).theme, "account theme unchanged").toBe("system");

  await page.unroute("**/api/v1/me/preferences", abort);
  const saved = page.waitForResponse(isPreferencesPatch);
  await toast.getByRole("button", { name: "Try again" }).click();
  expect((await saved).status()).toBeLessThan(300);
  await expect(toast).toHaveCount(0);
  await expect(page.locator("html")).toHaveAttribute("data-bs-theme", "dark");
  expect((await getMe(page)).theme).toBe("dark");
});

// ---- time zone ----------------------------------------------------------------------------------------------------

/** 20:00 UTC on 1 Jan is 2 Jan in Tokyo and 1 Jan in Kyiv and New York: the dates show which zone formats them. */
async function fixturePasskey(page: Page) {
  await page.route("**/api/v1/passkeys", (route) =>
    route.request().method() === "GET"
      ? route.fulfill({
          json: {
            items: [
              {
                id: "fixture-key",
                label: "Fixture key",
                createdAt: "2026-01-01T20:00:00Z",
                lastUsedAt: "2026-01-01T20:30:00Z",
              },
            ],
          },
        })
      : route.continue(),
  );
}

const zoneCard = (page: Page) =>
  page.getByRole("region", { name: "Time zone" });

async function openPicker(page: Page) {
  await zoneCard(page)
    .getByRole("button", { name: "Change time zone" })
    .click();
  const picker = page.getByRole("dialog", { name: "Choose your time zone" });
  await expect(picker).toBeVisible();
  return picker;
}

const searchBox = (picker: ReturnType<Page["locator"]>) =>
  picker.getByRole("combobox", { name: "Search by city or region" });

const FALLBACK_HINT =
  "We couldn't read your device's time zone, so teleX uses UTC.";

test("AC-183: the device zone is saved once, a device elsewhere doesn't change it, and SCR-64 dates use it", async ({
  page,
  browser,
}) => {
  const first = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
  const address = await signUp(first.page);
  await fixturePasskey(first.page);
  await openProfile(first.page);
  await expect(zoneCard(first.page)).toContainText("Asia/Tokyo");
  await expect(first.page.getByText("Created 2 Jan 2026")).toBeVisible();
  expect(await getMe(first.page)).toMatchObject({
    timeZone: "Asia/Tokyo",
    timeZoneIsFallback: false,
  });

  const second = await newDevice(browser, page, {
    timezoneId: "America/New_York",
  });
  await signInByLink(second.page, address, 2);
  await fixturePasskey(second.page);
  await openProfile(second.page);
  await expect(zoneCard(second.page)).toContainText("Asia/Tokyo");
  await expect(second.page.getByText("Created 2 Jan 2026")).toBeVisible();
  expect((await getMe(second.page)).timeZone).toBe("Asia/Tokyo");
  await first.context.close();
  await second.context.close();
});

const unusableZones = [
  ["unreadable", undefined],
  ["not on the list", "Mars/Olympus_Mons"],
] as const;

for (const [kind, reported] of unusableZones) {
  test(`AC-183: a device zone that is ${kind} saves UTC and shows the fallback hint on every device`, async ({
    page,
    browser,
  }) => {
    const first = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
    await first.page.addInitScript((value) => {
      const original = Intl.DateTimeFormat.prototype.resolvedOptions;
      Intl.DateTimeFormat.prototype.resolvedOptions = function () {
        return { ...original.call(this), timeZone: value as never };
      };
    }, reported);
    const address = await signUp(first.page);
    await openProfile(first.page);
    await expect(zoneCard(first.page)).toContainText("UTC");
    await expect(first.page.getByText(FALLBACK_HINT)).toBeVisible();
    expect(await getMe(first.page)).toMatchObject({
      timeZone: "UTC",
      timeZoneIsFallback: true,
    });

    const second = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
    await signInByLink(second.page, address, 2);
    await openProfile(second.page);
    await expect(second.page.getByText(FALLBACK_HINT)).toBeVisible();
    await first.context.close();
    await second.context.close();
  });
}

test("AC-184: picking Kyiv saves it, dates follow it, and another device shows it after a reload", async ({
  page,
  browser,
}) => {
  const first = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
  const address = await signUp(first.page);
  const second = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
  await signInByLink(second.page, address, 2);
  await fixturePasskey(first.page);
  await fixturePasskey(second.page);
  await openProfile(first.page);
  await openProfile(second.page);
  await expect(first.page.getByText("Created 2 Jan 2026")).toBeVisible();

  const picker = await openPicker(first.page);
  await searchBox(picker).fill("Kyiv");
  await picker.getByRole("option", { name: /Europe\/Kyiv/ }).click();
  await expect(
    first.page.getByRole("status").filter({ hasText: "Time zone saved." }),
  ).toBeVisible();
  await expect(zoneCard(first.page)).toContainText("Europe/Kyiv");
  await expect(first.page.getByText("Created 1 Jan 2026")).toBeVisible();
  expect((await getMe(first.page)).timeZone).toBe("Europe/Kyiv");

  // the device that was already open picks it up when teleX is next opened or reloaded there
  await second.page.reload();
  await expect(zoneCard(second.page)).toContainText("Europe/Kyiv");
  await expect(second.page.getByText("Created 1 Jan 2026")).toBeVisible();
  await first.context.close();
  await second.context.close();
});

test("AC-185: a search that matches nothing says so and keeps the current zone", async ({
  page,
  browser,
}) => {
  const device = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
  await signUp(device.page);
  await openProfile(device.page);
  const picker = await openPicker(device.page);
  await searchBox(picker).fill("Atlantis");
  await expect(
    picker.getByText("No time zone matches “Atlantis”. Try a nearby city."),
  ).toBeVisible();
  await expect(picker.getByRole("option")).toHaveCount(0);
  await picker.getByRole("button", { name: "Cancel" }).click();
  await expect(picker).toHaveCount(0);
  await expect(zoneCard(device.page)).toContainText("Asia/Tokyo");
  expect((await getMe(device.page)).timeZone).toBe("Asia/Tokyo");
  await device.context.close();
});

test("AC-186: the picker has no empty choice and an empty zone sent to the API is refused", async ({
  page,
  browser,
}) => {
  const device = await newDevice(browser, page, { timezoneId: "Asia/Tokyo" });
  await signUp(device.page);
  await openProfile(device.page);
  const picker = await openPicker(device.page);
  await expect(picker.getByRole("option").first()).toBeVisible();
  const names = await picker.getByRole("option").allInnerTexts();
  expect(
    names.every((n) => n.trim().length > 0),
    "no blank option",
  ).toBe(true);
  await expect(
    picker.getByRole("button", { name: /clear|none|remove/i }),
  ).toHaveCount(0);
  await picker.getByRole("button", { name: "Close" }).click();

  for (const timeZone of ["", null]) {
    const res = await apiCall(device.page, "PATCH", "/api/v1/me/preferences", {
      timeZone,
    });
    expect(res.status).toBe(400);
    const errors = (res.body?.errors ?? []) as { code: string }[];
    expect(errors.map((e) => e.code)).toContain("time-zone-required");
  }
  expect((await getMe(device.page)).timeZone).toBe("Asia/Tokyo");
  await device.context.close();
});
