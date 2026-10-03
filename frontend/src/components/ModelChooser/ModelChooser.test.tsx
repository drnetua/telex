import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { CatalogModel, SlotKind } from "../../api/models";
import { ModelChooser } from "./ModelChooser";

const model = (id: string, name: string, slots: SlotKind[]): CatalogModel => ({
  modelId: id,
  name,
  provider: "Test",
  takes: ["text"],
  produces: ["text"],
  slots,
  inputPricePerMillionTokens: null,
  outputPricePerMillionTokens: null,
  pricePerImage: null,
  contextLength: null,
});
const models = [
  model("t/a", "Text A", ["text"]),
  model("t/v", "Vision V", ["text", "vision"]),
  model("t/i", "Image I", ["image"]),
];
const setup = (slot: SlotKind, taken: string[] = []) => {
  const onChoose = vi.fn();
  const onClose = vi.fn();
  render(
    <ModelChooser
      slot={slot}
      models={models}
      taken={taken}
      onChoose={onChoose}
      onClose={onClose}
    />,
  );
  return { onChoose, onClose };
};
const row = (name: string) =>
  screen.getAllByRole("listitem").find((r) => r.textContent?.includes(name)) as HTMLElement;

describe("ModelChooser", () => {
  it("is a region named for the slot and focuses the search box", () => {
    setup("text");
    expect(screen.getByRole("region", { name: "Choose a model for Text" })).toBeInTheDocument();
    expect(screen.getByRole("searchbox")).toHaveFocus();
  });

  it("lists the whole catalog and filters by name", async () => {
    setup("text");
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
    await userEvent.type(screen.getByRole("searchbox"), "vision");
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    await userEvent.clear(screen.getByRole("searchbox"));
    await userEvent.type(screen.getByRole("searchbox"), "zzz");
    expect(screen.getByText("No models match your search.")).toBeInTheDocument();
  });

  it("chooses an enabled row", async () => {
    const { onChoose } = setup("text");
    await userEvent.click(within(row("Text A")).getByRole("button"));
    expect(onChoose).toHaveBeenCalledWith(models[0]);
  });

  it.each([
    ["vision", "Text A", "Can't understand images"],
    ["image", "Text A", "Can't create images"],
    ["text", "Image I", "Doesn't take and produce text"],
  ] as const)(
    "%s slot: %s is disabled with its reason and can't be chosen",
    async (slot, name, why) => {
      const { onChoose } = setup(slot);
      const button = within(row(name)).getByRole("button");
      expect(within(row(name)).getByText(why)).toBeInTheDocument();
      expect(button).toHaveAttribute("aria-disabled", "true");
      await userEvent.click(button);
      expect(onChoose).not.toHaveBeenCalled();
    },
  );

  it("disables a model already in the slot", async () => {
    const { onChoose } = setup("text", ["t/a"]);
    expect(within(row("Text A")).getByText("Already in this slot")).toBeInTheDocument();
    await userEvent.click(within(row("Text A")).getByRole("button"));
    expect(onChoose).not.toHaveBeenCalled();
  });

  it("Hide list and Esc close it", async () => {
    const { onClose } = setup("text");
    await userEvent.click(screen.getByRole("button", { name: "Hide list" }));
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
