import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FilterBar } from "./FilterBar";

const props = {
  search: "",
  onSearch: vi.fn(),
  searchLabel: "Search",
  filterLabel: "Slot",
  filterValue: "any",
  filterOptions: [
    { value: "any", label: "Any" },
    { value: "text", label: "Text" },
  ],
  onFilter: vi.fn(),
  active: false,
  onReset: vi.fn(),
  resetLabel: "Reset all",
};

describe("FilterBar (C-28)", () => {
  it("reports search and filter changes", async () => {
    render(<FilterBar {...props} />);
    await userEvent.type(screen.getByRole("searchbox"), "a");
    await userEvent.selectOptions(screen.getByLabelText("Slot"), "Text");
    expect(props.onSearch).toHaveBeenCalledWith("a");
    expect(props.onFilter).toHaveBeenCalledWith("text");
  });

  it("shows Reset all only while a search or filter is active", async () => {
    const { rerender } = render(<FilterBar {...props} />);
    expect(screen.queryByRole("button", { name: "Reset all" })).not.toBeInTheDocument();
    rerender(<FilterBar {...props} active />);
    await userEvent.click(screen.getByRole("button", { name: "Reset all" }));
    expect(props.onReset).toHaveBeenCalled();
  });
});
