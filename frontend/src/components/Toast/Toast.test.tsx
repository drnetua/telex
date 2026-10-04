import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { readFileSync } from "node:fs";
import { describe, expect, it, vi } from "vitest";
import { Toast } from "./Toast";

describe("Toast placement (AC-43)", () => {
  it("uses the offset container class and the stylesheet lifts it above the phone bar", () => {
    render(<Toast message="Saved" onDismiss={() => undefined} />);
    expect(document.querySelector(".telex-toast-container")).not.toBeNull();
    const css = readFileSync("src/styles.css", "utf8");
    const phone = /@media \(max-width: 767\.98px\) \{([^@]*)\}/g;
    const rules = Array.from(css.matchAll(phone)).map((match) => match[1]);
    expect(
      rules.some((r) => /\.telex-toast-container\s*\{[^}]*bottom:\s*calc\(/.test(r ?? "")),
    ).toBe(true);
  });
});

describe("Toast slot", () => {
  it("AC-114: Toasts open together share one container and stay readable", () => {
    render(
      <>
        <Toast message="First notice" onDismiss={() => undefined} />
        <Toast tone="error" message="Second notice" onDismiss={() => undefined} />
      </>,
    );
    expect(screen.getByText("First notice")).toBeVisible();
    expect(screen.getByText("Second notice")).toBeVisible();
    const containers = document.querySelectorAll(".telex-toast-container");
    expect(containers).toHaveLength(1);
    expect(containers[0]).toContainElement(screen.getByText("First notice"));
    expect(containers[0]).toContainElement(screen.getByText("Second notice"));
  });

  it("removes the container with the last Toast so it cannot cover the page", () => {
    const { unmount } = render(<Toast message="Saved" onDismiss={() => undefined} />);
    unmount();
    expect(document.querySelector(".telex-toast-container")).toBeNull();
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

describe("Toast announcement (AC-111, AC-114)", () => {
  it("writes an info Toast's text into a polite live region that existed before it mounted", async () => {
    const before = Array.from(document.querySelectorAll('[aria-live="polite"]'));
    const region = before.find((el) => el.textContent === "");
    expect(region).toBeDefined();
    render(<Toast message="Theme saved" onDismiss={() => undefined} />);
    await waitFor(() => expect(region).toHaveTextContent("Theme saved"));
    expect(region?.isConnected).toBe(true);
  });

  it("gives an info Toast no live region of its own inside the persistent polite one", () => {
    render(<Toast message="Fine" onDismiss={() => undefined} />);
    const frame = screen.getByText("Fine").closest(".toast");
    expect(frame).not.toHaveAttribute("aria-live");
    expect(frame).not.toHaveAttribute("role");
  });
});
