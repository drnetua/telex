import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { ModelProfile, ProfileRef, Slot } from "../../api/models";
import { ModelProfileCard } from "./ModelProfileCard";

const slot = (over: Partial<Slot> = {}): Slot => ({
  state: "main-model",
  currentModelId: "test/a",
  chain: [{ modelId: "test/a", name: "Model A", availability: "available" }],
  ...over,
});
const unused = slot({ state: "not-used", currentModelId: null, chain: [] });
const none = (modelId = "test/gone") =>
  slot({
    state: "no-model-available",
    currentModelId: null,
    chain: [{ modelId, name: null, availability: "not-in-catalog" }],
  });
const profile = (
  ref: ProfileRef,
  name: string,
  slots: Partial<ModelProfile["slots"]> = {},
): ModelProfile => ({
  ref,
  name,
  slots: { text: slot(), vision: unused, image: unused, ...slots },
  pricePer100Runs: { state: "estimate", amount: "0.30" },
  choosable: true,
});
const system = profile({ kind: "system", key: "balanced" }, "Balanced");
const custom = (slots: Partial<ModelProfile["slots"]> = {}) =>
  profile({ kind: "custom", id: "c1" }, "Cheap vision", slots);
const noop = { onDuplicate: vi.fn(), onEdit: vi.fn(), onDelete: vi.fn() };

describe("ModelProfileCard", () => {
  it("AC-219: a system card has a System badge, the explanation, only Duplicate - no Edit or Delete", () => {
    render(
      <ModelProfileCard profile={system} aiConfigured isDefault={false} onDuplicate={vi.fn()} />,
    );
    expect(screen.getByText("System")).toBeInTheDocument();
    expect(
      screen.getByText("System profiles can't be changed. Duplicate it to make your own."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Duplicate" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
  });

  it("a custom card has Edit, Duplicate and Delete that call back, and no System badge", async () => {
    const cb = { onDuplicate: vi.fn(), onEdit: vi.fn(), onDelete: vi.fn() };
    render(<ModelProfileCard profile={custom()} aiConfigured isDefault={false} {...cb} />);
    expect(screen.queryByText("System")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(screen.getByRole("button", { name: "Duplicate" }));
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(cb.onEdit).toHaveBeenCalledOnce();
    expect(cb.onDuplicate).toHaveBeenCalledOnce();
    expect(cb.onDelete).toHaveBeenCalledOnce();
  });

  it("shows the profile name, the price per 100 runs and a Default badge on the default", () => {
    render(<ModelProfileCard profile={custom()} aiConfigured isDefault {...noop} />);
    expect(screen.getByRole("heading", { name: /Cheap vision/ })).toBeInTheDocument();
    expect(screen.getByText("≈ $0.30 per 100 runs")).toBeInTheDocument();
    expect(screen.getByText("Default")).toBeInTheDocument();
  });

  it("AC-223: an empty vision or image slot shows 'Not used'", () => {
    render(<ModelProfileCard profile={custom()} aiConfigured isDefault={false} {...noop} />);
    expect(screen.getAllByText("Not used")).toHaveLength(2);
  });

  it("AC-10: a fallback slot row says 'Main model unavailable. Using Model B for now.'", () => {
    const text = slot({
      state: "fallback",
      currentModelId: "test/b",
      chain: [
        { modelId: "test/a", name: null, availability: "not-in-catalog" },
        { modelId: "test/b", name: "Model B", availability: "available" },
      ],
    });
    render(
      <ModelProfileCard profile={custom({ text })} aiConfigured isDefault={false} {...noop} />,
    );
    expect(screen.getByText("Main model unavailable. Using Model B for now.")).toBeInTheDocument();
  });

  it("a fallback to a model missing from the catalog names it by its id", () => {
    const text = slot({
      state: "fallback",
      currentModelId: "test/b",
      chain: [
        { modelId: "test/a", name: null, availability: "not-in-catalog" },
        { modelId: "test/b", name: null, availability: "available" },
      ],
    });
    render(
      <ModelProfileCard profile={custom({ text })} aiConfigured isDefault={false} {...noop} />,
    );
    expect(screen.getByText("Main model unavailable. Using test/b for now.")).toBeInTheDocument();
  });

  it("AC-223 custom: no model available shows 'No model available' and a 'Pick another model' link", async () => {
    const onPick = vi.fn();
    render(
      <ModelProfileCard
        profile={custom({ text: none() })}
        aiConfigured
        isDefault={false}
        {...noop}
        onPickAnotherModel={onPick}
      />,
    );
    expect(screen.getByText("No model available")).toBeInTheDocument();
    await userEvent.click(screen.getByText("Pick another model"));
    expect(onPick).toHaveBeenCalledOnce();
    expect(screen.queryByText("Choose another profile")).not.toBeInTheDocument();
  });

  it("AC-223 system: no model available shows 'Choose another profile' instead of 'Pick another model'", async () => {
    const onChoose = vi.fn();
    render(
      <ModelProfileCard
        profile={profile(system.ref, "Balanced", { text: none() })}
        aiConfigured
        isDefault={false}
        onDuplicate={vi.fn()}
        onChooseAnotherProfile={onChoose}
      />,
    );
    expect(screen.getByText("No model available")).toBeInTheDocument();
    await userEvent.click(screen.getByText("Choose another profile"));
    expect(onChoose).toHaveBeenCalledOnce();
    expect(screen.queryByText("Pick another model")).not.toBeInTheDocument();
  });

  it("a chain model that left the catalog shows its id with a neutral 'Not in the catalog' badge", () => {
    const text = slot({
      chain: [
        { modelId: "test/a", name: "Model A", availability: "available" },
        { modelId: "test/old", name: null, availability: "not-in-catalog" },
        { modelId: "test/img", name: null, availability: "not-capable" },
      ],
    });
    render(
      <ModelProfileCard profile={custom({ text })} aiConfigured isDefault={false} {...noop} />,
    );
    expect(screen.getByText("test/old")).toBeInTheDocument();
    expect(screen.getAllByText("Not in the catalog")).toHaveLength(2);
  });

  it("AC-226: with AI not set up Duplicate and Edit are disabled while Delete stays enabled", () => {
    render(
      <ModelProfileCard profile={custom()} aiConfigured={false} isDefault={false} {...noop} />,
    );
    expect(screen.getByRole("button", { name: "Duplicate" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Edit" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled();
  });

  it("opening the editor: Duplicate is busy while the draft loads", () => {
    render(
      <ModelProfileCard
        profile={system}
        aiConfigured
        isDefault={false}
        onDuplicate={vi.fn()}
        duplicating
      />,
    );
    const b = screen.getByRole("button", { name: "Duplicate" });
    expect(b.querySelector(".spinner-border")).not.toBeNull();
  });

  it("edge: a name with markup is rendered as plain text", () => {
    const p = profile({ kind: "custom", id: "c2" }, "<script>alert(1)</script>");
    const { container } = render(
      <ModelProfileCard profile={p} aiConfigured isDefault={false} {...noop} />,
    );
    expect(screen.getByText("<script>alert(1)</script>")).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
  });
});
