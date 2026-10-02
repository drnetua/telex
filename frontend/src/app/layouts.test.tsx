import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { readFileSync, statSync } from "node:fs";
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

  it("R7: the 96 px slot uses a derived 192 px logo well under 50 KB; the canonical file is untouched", () => {
    render(
      <MemoryRouter initialEntries={["/x"]}>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/x" element={<p>content</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole("img", { name: "teleX" })).toHaveAttribute(
      "src",
      expect.stringContaining("telex-logo-192"),
    );
    const derived = "src/assets/telex-logo-192.png";
    expect(statSync(derived).size).toBeLessThan(50_000);
    expect(readFileSync(derived).readUInt32BE(16)).toBe(192);
    const canonical = "src/assets/telex-logo.png";
    expect(readFileSync(canonical).readUInt32BE(16)).toBe(1254);
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
