import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { SettingsPage } from "./SettingsPage";

describe("SCR-69 Settings", () => {
  it("lists Accounts (SCR-60) before Profile and security", () => {
    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    );
    const links = screen.getAllByRole("link");
    expect(links[0]).toHaveAccessibleName(/^Accounts/);
    expect(links[0]).toHaveAttribute("href", "/accounts");
    expect(links[1]).toHaveAccessibleName(/^Profile and security/);
  });
});
