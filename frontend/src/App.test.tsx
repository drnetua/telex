import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { messages } from "./messages";

describe("App", () => {
  it("renders the app shell with the placeholder page", () => {
    render(<App />);

    expect(screen.getByText(messages.appName)).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 1, name: messages.home.title }),
    ).toBeInTheDocument();
  });
});
