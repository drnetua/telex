import { expect, type Page } from "@playwright/test";

export type Theme = "light" | "dark" | "system";

/** Section registry mirror (app-map order), with the phone placement the shell promises. */
export const sectionList = [
  { name: "Overview", path: "/overview", phone: "more", built: false },
  { name: "Inbox", path: "/inbox", phone: "bar", built: true },
  { name: "Chats", path: "/chats", phone: "bar", built: false },
  { name: "Assistants", path: "/assistants", phone: "bar", built: false },
  { name: "Runs", path: "/runs", phone: "more", built: false },
  { name: "Tasks", path: "/tasks", phone: "bar", built: false },
  { name: "Settings", path: "/settings", phone: "more", built: true },
] as const;

export const unbuiltSections = sectionList.filter((s) => !s.built);

/** Every signed-in shell screen the sweep covers. */
export const shellScreens = [
  ...sectionList.map((s) => ({ name: s.name, path: s.path as string })),
  { name: "Profile and security", path: "/profile" },
];

export const isPhone = (page: Page) => (page.viewportSize()?.width ?? 0) < 768;

/** Sets the calling Owner's fixture Inbox count and status conditions (`e2e` profile only). */
export async function setPulseFixture(
  page: Page,
  fixture: { inboxCount: number; conditions: string[] },
): Promise<void> {
  const status = await page.evaluate(async (body) => {
    const token =
      document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)?.[1] ?? "";
    const res = await fetch("/api/v1/e2e-fixtures/pulse", {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": decodeURIComponent(token),
      },
      body: JSON.stringify(body),
    });
    return res.status;
  }, fixture);
  expect(status, "setPulseFixture answered 204").toBe(204);
}

/** Saves the Owner's theme through the preferences endpoint, then reloads so the shell shows it. */
export async function setTheme(page: Page, theme: Theme): Promise<void> {
  const status = await page.evaluate(async (value) => {
    const token =
      document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)?.[1] ?? "";
    const res = await fetch("/api/v1/me/preferences", {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": decodeURIComponent(token),
      },
      body: JSON.stringify({ theme: value }),
    });
    return res.status;
  }, theme);
  expect(status, "changeMyPreferences answered 2xx").toBeLessThan(300);
  await page.reload();
}

/** Nothing on the page is wider than the viewport (no sideways scroll). */
export async function expectNoSidewaysScroll(
  page: Page,
  state: string,
): Promise<void> {
  const [scrollWidth, innerWidth] = await page.evaluate(() => [
    document.documentElement.scrollWidth,
    window.innerWidth,
  ]);
  expect(scrollWidth, `sideways scroll on ${state}`).toBeLessThanOrEqual(
    innerWidth,
  );
}

/** Opens the More sheet on a phone and returns it. */
export async function openMore(page: Page) {
  await page
    .getByRole("navigation", { name: "Main" })
    .getByRole("button", { name: "More" })
    .click();
  const sheet = page.getByRole("dialog", { name: "More" });
  await expect(sheet).toBeVisible();
  return sheet;
}

/** Signs out from wherever the shell offers it: the side menu, or the More sheet on a phone. */
export async function signOutFromShell(page: Page): Promise<void> {
  const scope = isPhone(page) ? await openMore(page) : page;
  await scope.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/sign-in$/);
}

/** Opens Profile and security by clicking through the shell: Settings (via More on a phone), then the entry. */
export async function openProfile(page: Page): Promise<void> {
  if (isPhone(page)) {
    const sheet = await openMore(page);
    await sheet.getByRole("link", { name: "Settings" }).click();
  } else {
    await page
      .getByRole("navigation", { name: "Main" })
      .getByRole("link", { name: /^Settings/ })
      .click();
  }
  await page.getByRole("link", { name: /Profile and security/ }).click();
  await expect(page).toHaveURL(/\/profile$/);
}
