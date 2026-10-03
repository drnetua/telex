import { render } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
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
