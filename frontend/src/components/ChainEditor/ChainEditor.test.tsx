import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { ChainEditor, type ChainItem } from "./ChainEditor";

const item = (id: string, over: Partial<ChainItem> = {}): ChainItem => ({
  modelId: id,
  name: id.toUpperCase(),
  missing: false,
  ...over,
});

function Harness({
  start,
  onAdd = () => undefined,
  error,
  rowErrors,
}: {
  start: ChainItem[];
  onAdd?: () => void;
  error?: string;
  rowErrors?: Record<number, string>;
}) {
  const [items, setItems] = useState(start);
  return (
    <ChainEditor
      label="Text"
      items={items}
      onChange={setItems}
      onAdd={onAdd}
      emptyText="Add at least one model."
      error={error}
      rowErrors={rowErrors}
    />
  );
}
const rows = () => screen.getAllByRole("listitem");

describe("ChainEditor", () => {
  it("shows the empty text and an enabled Add model button", () => {
    render(<Harness start={[]} />);
    expect(screen.getByText("Add at least one model.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add model" })).toBeEnabled();
  });

  it("labels the first row Main and the rest Backup n", () => {
    render(<Harness start={[item("a"), item("b"), item("c")]} />);
    expect(within(rows()[0]!).getByText("Main")).toBeInTheDocument();
    expect(within(rows()[1]!).getByText("Backup 1")).toBeInTheDocument();
    expect(within(rows()[2]!).getByText("Backup 2")).toBeInTheDocument();
  });

  it("marks a missing model with its id and 'Not in the catalog'", () => {
    render(<Harness start={[item("gone/x", { name: null, missing: true })]} />);
    expect(screen.getByText("gone/x")).toBeInTheDocument();
    expect(screen.getByText("Not in the catalog")).toBeInTheDocument();
  });

  it("calls onAdd, and is full at three models with the reason beside the disabled button", async () => {
    const onAdd = vi.fn();
    const { rerender } = render(<Harness start={[item("a")]} onAdd={onAdd} />);
    await userEvent.click(screen.getByRole("button", { name: "Add model" }));
    expect(onAdd).toHaveBeenCalledOnce();
    rerender(<Harness key="full" start={[item("a"), item("b"), item("c")]} onAdd={onAdd} />);
    expect(screen.getByText("A slot holds at most three models.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add model" })).toBeDisabled();
  });

  it("moves a row, announces it politely and keeps focus in the moved row", async () => {
    render(<Harness start={[item("a"), item("b")]} />);
    await userEvent.click(within(rows()[1]!).getByRole("button", { name: "Move up" }));
    expect(rows()[0]!.textContent).toContain("B");
    expect(rows()[0]!.contains(document.activeElement)).toBe(true);
    expect(screen.getByText("B is now Main").closest("[aria-live]")).toHaveAttribute(
      "aria-live",
      "polite",
    );
  });

  it("removes a row and moves focus to a neighbour, or to Add model when empty", async () => {
    render(<Harness start={[item("a"), item("b")]} />);
    await userEvent.click(screen.getByRole("button", { name: "Remove A" }));
    expect(rows()).toHaveLength(1);
    expect(rows()[0]!.contains(document.activeElement)).toBe(true);
    await userEvent.click(screen.getByRole("button", { name: "Remove B" }));
    expect(screen.getByRole("button", { name: "Add model" })).toHaveFocus();
  });

  it("shows the slot error and row errors", () => {
    render(
      <Harness start={[item("a"), item("b")]} error="Slot bad" rowErrors={{ 1: "Row bad" }} />,
    );
    expect(screen.getByText("Slot bad")).toBeInTheDocument();
    expect(within(rows()[1]!).getByText("Row bad")).toBeInTheDocument();
    expect(within(rows()[0]!).queryByText("Row bad")).toBeNull();
  });
});
