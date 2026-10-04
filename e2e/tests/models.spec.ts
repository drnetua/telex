import { expect, test } from "@playwright/test";
import { expectNoA11yViolations, signUp } from "../support/flows";
import {
  addModel,
  createCheapVision,
  expectNoDialogA11yViolations,
  openModels,
  profileCard,
  slotFieldset,
  toast,
  waitForCatalog,
} from "../support/models";

// Each test signs up its own Owner (user-<uuid>@example.test style address), so phone and desktop never share state.
test.beforeEach(async ({ page }) => {
  await signUp(page);
  await waitForCatalog(page);
});

test("AC-211: browse the catalog, search by name and filter by the vision slot", async ({ page }) => {
  await openModels(page, "catalog");
  await expect(page.getByText(/^Updated /)).toBeVisible();
  await expectNoA11yViolations(page, "SCR-66 Model catalog tab", 60_000);

  const rows = page.locator("tbody tr");
  // Only models that fit a slot are listed: the audio-only model is not.
  await expect(rows.first()).toBeVisible();
  await expect(page.getByText("Test audio only model")).toHaveCount(0);

  await page.getByRole("searchbox", { name: "Search" }).fill("Test");
  await page.getByLabel("Slot").selectOption("vision");
  await expect(rows).toHaveCount(100);
  for (const row of await rows.all()) {
    await expect(row.locator('td[data-label="Takes"]')).toHaveText("Text, images");
    await expect(row.locator('td[data-label="Produces"]')).toHaveText("Text");
  }
  await expect(page.getByText("Test text model", { exact: false })).toHaveCount(0);

  await page.getByRole("searchbox", { name: "Search" }).fill("Test vision model 007");
  await expect(rows).toHaveCount(1);
  const row = rows.first();
  await expect(row).toContainText("Test vision model 007");
  await expect(row).toContainText("test"); // provider
  await expect(row.locator('td[data-label="Price"]')).toContainText(/\$0\.20.*in.*\$0\.80.*out/s);
  await expect(row.locator('td[data-label="Price"]')).toContainText("per 1M tokens");
  await expect(row.locator('td[data-label="Context"]')).toContainText("128,000");

  await page.getByLabel("Slot").selectOption("image");
  await page.getByRole("searchbox", { name: "Search" }).fill("Test image model");
  await expect(rows).toHaveCount(30);
  for (const row of await rows.all()) await expect(row.locator('td[data-label="Produces"]')).toContainText("Images");

  await page.getByRole("searchbox", { name: "Search" }).fill("no such model anywhere");
  await expect(page.getByText("No models match your search.")).toBeVisible();
});

test("AC-213: duplicate Balanced, rename, reorder the text chain, save", async ({ page }) => {
  await openModels(page);

  // The first copy is "Balanced copy"; once that name is taken the next one is "Balanced copy 2".
  await profileCard(page, "Balanced").getByRole("button", { name: "Duplicate" }).click();
  const dialog = page.getByRole("dialog", { name: "Create profile" });
  await expect(dialog.getByLabel("Name")).toHaveValue("Balanced copy");
  await dialog.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(profileCard(page, "Balanced copy")).toBeVisible();

  await profileCard(page, "Balanced").getByRole("button", { name: "Duplicate" }).click();
  await expect(dialog.getByLabel("Name")).toHaveValue("Balanced copy 2");
  await expectNoDialogA11yViolations(page, "SCR-34 profile editor");

  // A Balanced model missing from the catalog is copied too and marked.
  await expect(slotFieldset(page, "Image").getByText("Not in the catalog")).toBeVisible();

  await dialog.getByLabel("Name").fill("Cheap vision");
  await addModel(page, "Text", "Test text model 001");
  const text = slotFieldset(page, "Text");
  await text.locator("li[data-model='test/text-001']").getByRole("button", { name: "Move up" }).click();
  await expect(text.locator("li[data-model]").first()).toContainText("Test text model 001");
  await expect(text.locator("li[data-model]").first()).toContainText("Main");
  await expect(text.locator("li[data-model]").nth(1)).toContainText("Backup 1");

  await slotFieldset(page, "Vision").getByRole("button", { name: /^Remove / }).first().click();
  await addModel(page, "Vision", "Test vision model 001");
  await slotFieldset(page, "Image").getByRole("button", { name: /^Remove / }).first().click();
  await expectNoDialogA11yViolations(page, "SCR-34 profile editor, edited");

  await dialog.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(toast(page).filter({ hasText: "Profile saved" })).toBeVisible();

  const card = profileCard(page, "Cheap vision");
  await expect(card).toBeVisible();
  const content = (await card.innerText()).replace(/\s+/g, " ");
  expect(content).toContain("Test text model 001");
  expect(content).toContain("Gemini 3.5 Flash");
  expect(content.indexOf("Test text model 001")).toBeLessThan(content.indexOf("Gemini 3.5 Flash"));
  expect(content).toContain("Test vision model 001");
  expect(content).toMatch(/Image Not used/);
  expect(content).toMatch(/per 100 runs/);
  await expectNoA11yViolations(page, "SCR-66 Profiles tab");
});

test("AC-51: the picker shows a price per 100 runs and Careful becomes the default", async ({ page }) => {
  await openModels(page);
  await createCheapVision(page);

  const picker = page.getByRole("radiogroup", { name: "Default profile" });
  await expect(picker.getByRole("radio", { name: /^Balanced/ })).toBeChecked();
  for (const name of ["Fast and cheap", "Balanced", "Careful", "Cheap vision"])
    await expect(picker.locator("label").filter({ hasText: name })).toContainText(/per 100 runs/);

  await picker.getByRole("radio", { name: /^Careful/ }).click();
  await expect(toast(page).filter({ hasText: "Careful is now your default profile." })).toBeVisible();
  await expect(picker.getByRole("radio", { name: /^Careful/ })).toBeChecked();
  await expect(profileCard(page, "Careful").getByText("Default", { exact: true })).toBeVisible();
  await expectNoA11yViolations(page, "SCR-66 Profiles tab with the picker");
});

test("AC-220: deleting the default custom profile returns the default to Balanced", async ({ page }) => {
  await openModels(page);
  await createCheapVision(page);
  const picker = page.getByRole("radiogroup", { name: "Default profile" });
  await picker.getByRole("radio", { name: /^Cheap vision/ }).click();
  await expect(picker.getByRole("radio", { name: /^Cheap vision/ })).toBeChecked();

  await profileCard(page, "Cheap vision").getByRole("button", { name: "Delete" }).click();
  const confirm = page.getByRole("dialog", { name: "Delete Cheap vision?" });
  await expect(confirm).toContainText("Balanced becomes the default");
  await confirm.getByRole("button", { name: "Delete profile" }).click();

  await expect(profileCard(page, "Cheap vision")).toHaveCount(0);
  await expect(picker.getByRole("radio", { name: /^Cheap vision/ })).toHaveCount(0);
  await expect(picker.getByRole("radio", { name: /^Balanced/ })).toBeChecked();
  await expect(
    toast(page).filter({ hasText: "Cheap vision deleted. Balanced is now your default profile." }),
  ).toBeVisible();
});

test("QG-4: the page shows the picker and the 500-model catalog within 1 s at p95", async ({ page }) => {
  test.setTimeout(180_000);
  const samples: number[] = [];
  for (let i = 0; i < 20; i++) {
    const started = Date.now();
    await page.goto("/settings/models");
    await expect(page.getByRole("radiogroup", { name: "Default profile" })).toBeVisible();
    await page.getByRole("tab", { name: "Model catalog" }).click();
    await expect(page.locator("tbody tr").first()).toBeVisible();
    samples.push(Date.now() - started);
  }
  const sorted = [...samples].sort((a, b) => a - b);
  const p95 = sorted[Math.ceil(0.95 * sorted.length) - 1] ?? Infinity;
  expect(p95, `p95 over ${samples.length} loads; samples (ms): ${samples.join(", ")}`).toBeLessThanOrEqual(1_000);
});
