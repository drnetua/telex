import { expect, test } from "@playwright/test";
import { signUp } from "../support/flows";

test("SCR-10: PageFrame icon buttons are at least 44 px square at phone width", async ({
  page,
}, testInfo) => {
  test.skip(
    testInfo.project.name !== "phone",
    "Touch targets matter at phone width",
  );
  await signUp(page);
  for (const name of ["Profile and security", "Sign out"]) {
    const box = await page.getByRole("button", { name }).boundingBox();
    expect(box, `${name} is rendered`).not.toBeNull();
    expect(box!.width, `${name} width`).toBeGreaterThanOrEqual(44);
    expect(box!.height, `${name} height`).toBeGreaterThanOrEqual(44);
  }
});
