import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ModelProfile, ProfileRef, Slot } from "../../api/models";
import { ModelProfilePicker } from "./ModelProfilePicker";

const slot = (over: Partial<Slot> = {}): Slot => ({
  state: "main-model",
  currentModelId: "test/a",
  chain: [{ modelId: "test/a", name: "Model A", availability: "available" }],
  ...over,
});
const profile = (
  name: string,
  ref: ProfileRef,
  over: Partial<ModelProfile> = {},
): ModelProfile => ({
  ref,
  name,
  slots: {
    text: slot(),
    vision: slot({ state: "not-used", currentModelId: null, chain: [] }),
    image: slot({ state: "not-used", currentModelId: null, chain: [] }),
  },
  pricePer100Runs: { state: "estimate", amount: "0.30" },
  choosable: true,
  ...over,
});
const fast = profile("Fast and cheap", { kind: "system", key: "fast" });
const balanced = profile("Balanced", { kind: "system", key: "balanced" });
const careful = profile(
  "Careful",
  { kind: "system", key: "careful" },
  {
    pricePer100Runs: { state: "estimate", amount: "2.5" },
  },
);
const radio = (name: string) => screen.getByRole("radio", { name: new RegExp(name) });

describe("ModelProfilePicker (C-22)", () => {
  it("AC-51: one radio per profile with its price per 100 runs; the current one has a Default badge", () => {
    render(
      <ModelProfilePicker
        profiles={[fast, balanced, careful]}
        value={balanced.ref}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(radio("Balanced")).toBeChecked();
    expect(
      within(radio("Balanced").closest("label") as HTMLElement).getByText("Default"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("≈ $0.30 per 100 runs").length).toBeGreaterThan(0);
    expect(screen.getByText("≈ $2.50 per 100 runs")).toBeInTheDocument();
  });

  it("selecting another option calls onChange with its ref", async () => {
    const onChange = vi.fn();
    render(
      <ModelProfilePicker
        profiles={[fast, balanced, careful]}
        value={balanced.ref}
        onChange={onChange}
      />,
    );
    await userEvent.click(radio("Careful"));
    expect(onChange).toHaveBeenCalledWith(careful.ref);
  });

  it.each([
    ["estimate below a cent", { state: "under-one-cent", amount: "0.004" }, "< $0.01 per 100 runs"],
    ["free", { state: "free", amount: "0" }, "Free"],
    ["unknown", { state: "unknown", amount: null }, "Price unknown"],
  ] as const)(
    "AC-210: %s price state is shown and the option stays choosable",
    async (_n, price, text) => {
      const onChange = vi.fn();
      const p = profile("Odd", { kind: "custom", id: "c1" }, { pricePer100Runs: { ...price } });
      render(
        <ModelProfilePicker profiles={[balanced, p]} value={balanced.ref} onChange={onChange} />,
      );
      expect(screen.getByText(text)).toBeInTheDocument();
      expect(radio("Odd")).toBeEnabled();
      await userEvent.click(radio("Odd"));
      expect(onChange).toHaveBeenCalledWith(p.ref);
    },
  );

  it("AC-10: a fallback text slot shows the warning with the model in use, and the price follows it", () => {
    const p = profile(
      "Cheap vision",
      { kind: "custom", id: "c1" },
      {
        slots: {
          ...balanced.slots,
          text: slot({
            state: "fallback",
            currentModelId: "test/b",
            chain: [
              { modelId: "test/a", name: null, availability: "not-in-catalog" },
              { modelId: "test/b", name: "Model B", availability: "available" },
            ],
          }),
        },
        pricePer100Runs: { state: "estimate", amount: "0.12" },
      },
    );
    render(<ModelProfilePicker profiles={[balanced, p]} value={balanced.ref} onChange={vi.fn()} />);
    const option = radio("Cheap vision").closest("label") as HTMLElement;
    expect(
      within(option).getByText("Main model unavailable. Using Model B for now."),
    ).toBeInTheDocument();
    expect(within(option).getByText("≈ $0.12 per 100 runs")).toBeInTheDocument();
  });

  it("AC-223: no text model shows 'No model available for text', no price, and a disabled radio", () => {
    const p = profile(
      "Broken",
      { kind: "custom", id: "c1" },
      {
        choosable: false,
        pricePer100Runs: { state: "no-text-model", amount: null },
      },
    );
    render(<ModelProfilePicker profiles={[balanced, p]} value={balanced.ref} onChange={vi.fn()} />);
    const option = radio("Broken").closest("label") as HTMLElement;
    expect(radio("Broken")).toBeDisabled();
    expect(within(option).getByText("No model available for text")).toBeInTheDocument();
    expect(within(option).queryByText(/per 100 runs/)).not.toBeInTheDocument();
  });

  it("busy: the chosen option shows a spinner and the other radios are read-only", () => {
    render(
      <ModelProfilePicker
        profiles={[fast, balanced, careful]}
        value={balanced.ref}
        busy={careful.ref}
        onChange={vi.fn()}
      />,
    );
    const option = radio("Careful").closest("label") as HTMLElement;
    expect(option.querySelector(".spinner-border")).not.toBeNull();
    expect(radio("Fast and cheap")).toBeDisabled();
    expect(radio("Balanced")).toBeDisabled();
  });

  it("edge: a name with markup is rendered as plain text", () => {
    const p = profile("<script>alert(1)</script>", { kind: "custom", id: "c1" });
    const { container } = render(
      <ModelProfilePicker profiles={[balanced, p]} value={balanced.ref} onChange={vi.fn()} />,
    );
    expect(screen.getByText("<script>alert(1)</script>")).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
  });
});
