import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { messages } from "./messages";

describe("App", () => {
  it("shows only the loading state until getMe answers", () => {
    render(<App />);

    expect(screen.queryByRole("navigation")).toBeNull();
    expect(screen.queryByRole("button", { name: messages.shell.signOut })).toBeNull();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
