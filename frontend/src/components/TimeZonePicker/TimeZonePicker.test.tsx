import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";
import { TimeZonePicker } from "./TimeZonePicker";

const zones = [
  "America/Argentina/Buenos_Aires",
  "Europe/Berlin",
  "Europe/Kyiv",
  "Europe/London",
  "UTC",
];

function setup(props: Partial<ComponentProps<typeof TimeZonePicker>> = {}) {
  const onPick = vi.fn();
  const onCancel = vi.fn();
  render(
    <TimeZonePicker
      zones={zones}
      current="Europe/Berlin"
      onPick={onPick}
      onCancel={onCancel}
      {...props}
    />,
  );
  return { onPick, onCancel };
}

describe("TimeZonePicker", () => {
  it("AC-184: finds a zone by city, with the current one marked and no empty option", async () => {
    const { onPick } = setup();
    const dialog = screen.getByRole("dialog", { name: "Choose your time zone" });
    const search = within(dialog).getByRole("combobox", { name: "Search by city or region" });
    expect(search).toHaveFocus();
    expect(within(dialog).getAllByRole("option")).toHaveLength(zones.length);
    expect(within(dialog).getByRole("option", { name: /Berlin/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await userEvent.type(search, "kyiv");
    const options = within(dialog).getAllByRole("option");
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent("Kyiv — Europe/Kyiv");
    await userEvent.click(options[0]!);
    expect(onPick).toHaveBeenCalledWith("Europe/Kyiv");
  });

  it("matches multi-word searches against the underscored name and region", async () => {
    setup();
    const search = screen.getByRole("combobox");
    await userEvent.type(search, "buenos aires");
    expect(screen.getAllByRole("option")).toHaveLength(1);
    await userEvent.clear(search);
    await userEvent.type(search, "europe");
    expect(screen.getAllByRole("option")).toHaveLength(3);
  });

  it("AC-185: says nothing matches and offers no option to pick", async () => {
    const { onPick } = setup();
    await userEvent.type(screen.getByRole("combobox"), "Atlantis");
    expect(
      screen.getByText("No time zone matches “Atlantis”. Try a nearby city."),
    ).toBeInTheDocument();
    expect(screen.queryAllByRole("option")).toHaveLength(0);
    await userEvent.keyboard("{Enter}");
    expect(onPick).not.toHaveBeenCalled();
  });

  it("AC-186: clearing the search shows the whole list and never offers an empty choice", async () => {
    const { onPick } = setup();
    const search = screen.getByRole("combobox");
    await userEvent.type(search, "kyiv");
    await userEvent.clear(search);
    expect(screen.getAllByRole("option")).toHaveLength(zones.length);
    expect(screen.queryByRole("option", { name: "" })).not.toBeInTheDocument();
    expect(onPick).not.toHaveBeenCalled();
  });

  it("picks with arrow keys and Enter, and Escape closes", async () => {
    const { onPick, onCancel } = setup();
    await userEvent.type(screen.getByRole("combobox"), "europe");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}{Enter}");
    expect(onPick).toHaveBeenCalledWith("Europe/Kyiv");
    await userEvent.keyboard("{Escape}");
    expect(onCancel).toHaveBeenCalled();
  });

  it("shows loading rows and a failed state with Try again", async () => {
    const onRetry = vi.fn();
    const { rerender } = render(
      <TimeZonePicker loading current="UTC" onPick={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
    rerender(
      <TimeZonePicker failed onRetry={onRetry} current="UTC" onPick={vi.fn()} onCancel={vi.fn()} />,
    );
    expect(screen.getByText("The time zone list didn't load.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(onRetry).toHaveBeenCalled();
  });

  it("Cancel closes without picking", async () => {
    const { onPick, onCancel } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onCancel).toHaveBeenCalled();
    expect(onPick).not.toHaveBeenCalled();
  });
});
