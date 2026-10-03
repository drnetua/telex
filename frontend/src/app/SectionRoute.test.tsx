import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { messages } from "../messages";
import { SectionRoute } from "./SectionRoute";

describe("SectionRoute (section-loading, section-load-failed)", () => {
  it("shows the loading state, then the section", async () => {
    const load = () => Promise.resolve({ default: () => <p>loaded section</p> });
    render(<SectionRoute load={load} />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(await screen.findByText("loaded section")).toBeInTheDocument();
  });

  it("shows section-load-failed with Try again, and Try again loads the chunk", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const load = vi
      .fn()
      .mockRejectedValueOnce(new Error("chunk"))
      .mockResolvedValue({ default: () => <p>loaded section</p> });
    render(<SectionRoute load={load} />);

    expect(await screen.findByText(messages.shell.loadFailed.title)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: messages.shell.loadFailed.retry }));
    expect(await screen.findByText("loaded section")).toBeInTheDocument();
  });
});
