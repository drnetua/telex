import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";
import { PageFrame } from "../components/PageFrame/PageFrame";
import { AuthLayout } from "./layouts";

describe("layouts (AC-83, AC-100)", () => {
  it("C2: the auth layout shows the 96 px logo asset", () => {
    render(
      <MemoryRouter initialEntries={["/x"]}>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/x" element={<p>content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    const logo = screen.getByRole("img", { name: "teleX" });
    expect(logo).toHaveAttribute("src", expect.stringContaining("telex-logo"));
    expect(logo).toHaveAttribute("width", "96");
    expect(logo).toHaveAttribute("height", "96");
  });

  it("C6: PageFrame icon buttons carry the 44 px touch-target class", () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <PageFrame>
            <p>content</p>
          </PageFrame>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    for (const name of ["Profile and security", "Sign out"]) {
      expect(screen.getByRole("button", { name })).toHaveClass("touch-target");
    }
  });
});
