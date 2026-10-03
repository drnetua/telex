import { expect, test } from "@playwright/test";
import { signUp } from "../support/flows";
import { isPhone, openMore, sectionList } from "../support/shell";

test("SCR-10: the shell's actions are at least 44 px square at phone width", async ({
  page,
}) => {
  await signUp(page);
  const nav = page.getByRole("navigation", { name: "Main" });

  if (!isPhone(page)) {
    // no bottom bar at desktop width: the side menu is the navigation
    await expect(nav.getByRole("button", { name: "More" })).toHaveCount(0);
    return;
  }

  const bar = sectionList.filter((s) => s.phone === "bar").map((s) => s.name);
  const items = [
    ...bar.map((name) =>
      nav.getByRole("link", { name: new RegExp(`^${name}`) }),
    ),
    nav.getByRole("button", { name: "More" }),
  ];
  expect(items).toHaveLength(5);
  for (const item of items) {
    const box = await item.boundingBox();
    const label = await item.innerText();
    expect(box, `${label} is rendered`).not.toBeNull();
    expect(box!.width, `${label} width`).toBeGreaterThanOrEqual(44);
    expect(box!.height, `${label} height`).toBeGreaterThanOrEqual(44);
  }

  const sheet = await openMore(page);
  const targets = [
    sheet.getByRole("button", { name: "Close" }),
    sheet.getByRole("button", { name: "Sign out" }),
    ...sectionList
      .filter((s) => s.phone === "more")
      .map((s) => sheet.getByRole("link", { name: s.name })),
  ];
  for (const target of targets) {
    const box = await target.boundingBox();
    const label = (await target.innerText()) || "Close";
    expect(box, `${label} is rendered`).not.toBeNull();
    expect(box!.height, `${label} height`).toBeGreaterThanOrEqual(44);
    expect(box!.width, `${label} width`).toBeGreaterThanOrEqual(44);
  }
});
