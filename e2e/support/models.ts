import AxeBuilder from "@axe-core/playwright";
import { expect, type Locator, type Page } from "@playwright/test";

export const MODELS_PATH = "/settings/models";

/** The Model Catalog is loaded from the fake provider at app start; wait for `state = current` before a journey. */
export async function waitForCatalog(page: Page): Promise<void> {
  await expect
    .poll(
      async () => {
        const res = await page.request.get("/api/v1/models/catalog");
        return res.ok() ? ((await res.json()) as { state: string }).state : `HTTP ${res.status()}`;
      },
      { message: "the Model Catalog reaches state current", timeout: 60_000, intervals: [500, 1_000, 2_000] },
    )
    .toBe("current");
}

export async function openModels(page: Page, tab: "profiles" | "catalog" = "profiles"): Promise<void> {
  await page.goto(tab === "catalog" ? `${MODELS_PATH}?tab=catalog` : MODELS_PATH);
  await expect(page.getByRole("heading", { name: "Models", level: 1 })).toBeVisible();
}

export function profileCard(page: Page, name: string): Locator {
  return page.locator(".card", { has: page.getByRole("heading", { name, exact: true, level: 3 }) });
}

export function slotFieldset(page: Page, slot: "Text" | "Vision" | "Image"): Locator {
  return page.getByRole("dialog").locator(`fieldset[data-slot="${slot}"]`);
}

/** A Toast: info Toasts are written into the persistent polite live region, error Toasts are `role=alert`. */
export function toast(page: Page): Locator {
  return page.locator("[role=status], [role=alert], [aria-live=polite]").filter({ hasText: /\S/ });
}

/** Adds a catalog model to a slot through the chooser. */
export async function addModel(page: Page, slot: "Text" | "Vision" | "Image", name: string): Promise<void> {
  await slotFieldset(page, slot).getByRole("button", { name: "Add model" }).click();
  const chooser = page.getByRole("region", { name: `Choose a model for ${slot}` });
  await chooser.getByRole("searchbox", { name: "Search models" }).fill(name);
  await chooser.getByRole("button", { name: new RegExp(`^${name}`) }).click();
  await expect(slotFieldset(page, slot).getByText(name, { exact: true })).toBeVisible();
}

/**
 * AC-213 journey: duplicate Balanced, rename to "Cheap vision", two models in text (second moved up to Main), one in
 * vision, image left empty, save. Leaves the page on the Models page with the "Profile saved" notice.
 */
export async function createCheapVision(page: Page): Promise<void> {
  await profileCard(page, "Balanced").getByRole("button", { name: "Duplicate" }).click();
  const dialog = page.getByRole("dialog", { name: "Create profile" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Name").fill("Cheap vision");
  await editSlots(page);
  await dialog.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
}

/** The slot edits of the AC-213 journey, in the open editor. */
export async function editSlots(page: Page): Promise<void> {
  await addModel(page, "Text", "Test text model 001");
  await slotFieldset(page, "Text").locator("li[data-model='test/text-001']").getByRole("button", { name: "Move up" }).click();
  const vision = slotFieldset(page, "Vision");
  await vision.getByRole("button", { name: /^Remove / }).first().click();
  await addModel(page, "Vision", "Test vision model 001");
  const image = slotFieldset(page, "Image");
  while ((await image.getByRole("button", { name: /^Remove / }).count()) > 0)
    await image.getByRole("button", { name: /^Remove / }).first().click();
}

/**
 * WCAG 2.2 AA scan of the open editor dialog only: the page behind the modal backdrop is dimmed, so axe measures its
 * colours against the backdrop; that page is scanned on its own in the Profiles tab check.
 */
export async function expectNoDialogA11yViolations(page: Page, state: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const results = await new AxeBuilder({ page })
          .include("[role=dialog]")
          .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
          .analyze();
        return results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`);
      },
      { message: `axe violations on ${state}`, timeout: 5_000 },
    )
    .toEqual([]);
}
