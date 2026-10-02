import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open</button>
      {open ? (
        <ConfirmDialog
          title="Sure?"
          confirmLabel="Yes"
          cancelLabel="No"
          onConfirm={() => setOpen(false)}
          onCancel={() => setOpen(false)}
        >
          Body
        </ConfirmDialog>
      ) : null}
    </>
  );
}

describe("ConfirmDialog focus (C7)", () => {
  it("focuses its first button, traps Tab and returns focus to the opener", async () => {
    render(<Harness />);
    const opener = screen.getByRole("button", { name: "Open" });
    await userEvent.click(opener);
    const no = screen.getByRole("button", { name: "No" });
    const yes = screen.getByRole("button", { name: "Yes" });
    expect(no).toHaveFocus();
    await userEvent.tab();
    expect(yes).toHaveFocus();
    await userEvent.tab();
    expect(no).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(yes).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });
});
