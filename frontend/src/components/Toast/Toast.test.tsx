import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { readFileSync } from "node:fs";
import { describe, expect, it, vi } from "vitest";
import { Toast } from "./Toast";

describe("Toast placement (AC-43)", () => {
  it("uses the offset container class and the stylesheet lifts it above the phone bar", () => {
    const { container } = render(<Toast message="Saved" onDismiss={() => undefined} />);
    expect(container.querySelector(".telex-toast-container")).not.toBeNull();
    const css = readFileSync("src/styles.css", "utf8");
    const phone = /@media \(max-width: 767\.98px\) \{([^@]*)\}/g;
    const rules = Array.from(css.matchAll(phone)).map((match) => match[1]);
    expect(
      rules.some((r) => /\.telex-toast-container\s*\{[^}]*bottom:\s*calc\(/.test(r ?? "")),
    ).toBe(true);
  });
});

describe("Toast action", () => {
  it("stays until acted on, then runs the action and dismisses", async () => {
    const act = vi.fn();
    const dismiss = vi.fn();
    render(
      <Toast
        tone="error"
        message="Your theme wasn't saved."
        action={{ label: "Try again", onClick: act }}
        onDismiss={dismiss}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(act).toHaveBeenCalledOnce();
    expect(dismiss).toHaveBeenCalledOnce();
  });
});
