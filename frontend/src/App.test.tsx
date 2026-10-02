import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { messages } from "./messages";

describe("App", () => {
  it("renders the app shell with the signed-in frame and Inbox loading", () => {
    render(<App />);

    expect(screen.getByText(messages.appName)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: messages.frame.signOut })).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
