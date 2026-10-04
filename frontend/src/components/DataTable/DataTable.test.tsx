import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DataTable } from "./DataTable";

describe("DataTable (C-29)", () => {
  it("renders headers and rows whose cells carry their column label", () => {
    render(
      <DataTable
        columns={[
          { key: "a", header: "Name", render: (r: { n: string }) => r.n },
          { key: "b", header: "Size", render: () => "1" },
        ]}
        rows={[{ n: "x" }]}
        rowKey={(r) => r.n}
      />,
    );
    expect(screen.getByRole("columnheader", { name: "Size" })).toBeInTheDocument();
    expect(screen.getByText("x")).toHaveAttribute("data-label", "Name");
  });
});
