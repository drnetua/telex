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

describe("ConfirmDialog while busy (R6)", () => {
  function Busy() {
    const [busy, setBusy] = useState(false);
    return (
      <ConfirmDialog
        title="Sure?"
        confirmLabel="Yes"
        cancelLabel="No"
        busy={busy}
        onConfirm={() => setBusy(true)}
        onCancel={() => undefined}
      >
        Body
      </ConfirmDialog>
    );
  }

  it("keeps focus on the dialog and Tab inside it once both buttons are disabled", async () => {
    render(<Busy />);
    await userEvent.click(screen.getByRole("button", { name: "Yes" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveFocus();
    await userEvent.tab();
    expect(dialog).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(dialog).toHaveFocus();
  });

  it("returns focus to the first button when the confirm fails and the dialog stays open", async () => {
    const { rerender } = render(
      <ConfirmDialog
        title="T"
        confirmLabel="Yes"
        cancelLabel="No"
        busy
        onConfirm={() => 0}
        onCancel={() => 0}
      >
        Body
      </ConfirmDialog>,
    );
    expect(screen.getByRole("dialog")).toHaveFocus();
    rerender(
      <ConfirmDialog
        title="T"
        confirmLabel="Yes"
        cancelLabel="No"
        onConfirm={() => 0}
        onCancel={() => 0}
      >
        Body
      </ConfirmDialog>,
    );
    expect(screen.getByRole("button", { name: "No" })).toHaveFocus();
  });

  it("restores focus to the element chosen by returnFocusTo instead of a detached opener", async () => {
    function Host() {
      const [open, setOpen] = useState(false);
      const [stable, setStable] = useState<HTMLElement | null>(null);
      return (
        <>
          <h2 tabIndex={-1} ref={setStable}>
            Stable
          </h2>
          {open ? null : <button onClick={() => setOpen(true)}>Open</button>}
          {open ? (
            <ConfirmDialog
              title="T"
              confirmLabel="Yes"
              cancelLabel="No"
              returnFocusTo={() => stable}
              onConfirm={() => setOpen(false)}
              onCancel={() => setOpen(false)}
            >
              Body
            </ConfirmDialog>
          ) : null}
        </>
      );
    }
    render(<Host />);
    await userEvent.click(screen.getByRole("button", { name: "Open" }));
    await userEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(screen.getByRole("heading", { name: "Stable" })).toHaveFocus();
  });
});
