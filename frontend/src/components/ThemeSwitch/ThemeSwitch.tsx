import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from "react";
import { useChangeTheme } from "../../api/preferences";
import { messages } from "../../messages";
import type { ThemeChoice } from "../../shell/theme";
import { Icon, type IconName } from "../Icon/Icon";
import { Toast } from "../Toast/Toast";

const m = messages.theme;

const options: Array<{ value: ThemeChoice; icon: IconName; label: string }> = [
  { value: "light", icon: "sun", label: m.light },
  { value: "dark", icon: "moon", label: m.dark },
  { value: "system", icon: "device-desktop", label: m.system },
];

interface ThemeSwitchProps {
  variant: "segmented" | "menu";
}

/** The one control that changes the theme: applies at once, remembers, saves, reverts on failure. */
export function ThemeSwitch({ variant }: ThemeSwitchProps) {
  const { shown, choose } = useChangeTheme();
  return variant === "segmented" ? (
    <Segmented shown={shown} onChoose={choose} />
  ) : (
    <Menu shown={shown} onChoose={choose} />
  );
}

/**
 * The failed-save Toast with Try again, shared by every ThemeSwitch. Mounted once in the layout so a save that
 * fails after its switch closed (menu, sheet, page change) is still told.
 */
export function ThemeSaveToast() {
  const { failed, retry, dismiss } = useChangeTheme();
  return failed ? (
    <Toast
      tone="error"
      message={m.saveFailed}
      dismissLabel={m.dismiss}
      action={{ label: m.tryAgain, onClick: retry }}
      onDismiss={dismiss}
    />
  ) : null;
}

interface PartProps {
  shown: ThemeChoice;
  onChoose: (choice: ThemeChoice) => void;
}

function Segmented({ shown, onChoose }: PartProps) {
  const group = useId();
  return (
    <div className="form-selectgroup" role="radiogroup" aria-label={m.label}>
      {options.map((o) => (
        <label key={o.value} className="form-selectgroup-item">
          <input
            type="radio"
            name={group}
            className="form-selectgroup-input"
            value={o.value}
            checked={shown === o.value}
            onChange={() => onChoose(o.value)}
          />
          <span className="form-selectgroup-label d-flex align-items-center gap-2">
            <Icon name={o.icon} size={18} />
            {o.label}
          </span>
        </label>
      ))}
    </div>
  );
}

function Menu({ shown, onChoose }: PartProps) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const current = options.find((o) => o.value === shown) ?? options[2];

  // Roving focus: the checked item takes focus on open, arrows/Home/End move it within the menu.
  useEffect(() => {
    if (!open) return;
    const items = root.current?.querySelectorAll<HTMLElement>('[role="menuitemradio"]');
    const checked = Array.from(items ?? []).find(
      (el) => el.getAttribute("aria-checked") === "true",
    );
    (checked ?? items?.[0])?.focus();
  }, [open]);

  const onMenuKey = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    const items = Array.from(
      event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitemradio"]'),
    );
    // Tab leaves the menu (menu-button pattern): close it and let focus move on.
    if (event.key === "Tab") {
      setOpen(false);
      return;
    }
    const at = items.indexOf(document.activeElement as HTMLElement);
    const target: Record<string, number> = {
      ArrowDown: (at + 1) % items.length,
      ArrowUp: (at - 1 + items.length) % items.length,
      Home: 0,
      End: items.length - 1,
    };
    const next = target[event.key];
    if (next === undefined) return;
    event.preventDefault();
    items[next]?.focus();
  };

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setOpen(false);
      trigger.current?.focus();
    };
    const onPointer = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onPointer);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onPointer);
    };
  }, [open]);

  return (
    <div
      ref={root}
      className="dropdown dropup"
      onBlur={(event) => {
        // Only focus landing elsewhere closes it; a null target (Safari doesn't focus a clicked button) keeps it.
        const next = event.relatedTarget;
        if (open && next && !root.current?.contains(next)) setOpen(false);
      }}
    >
      <button
        ref={trigger}
        type="button"
        className="btn btn-ghost-dark"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Icon name={current?.icon ?? "device-desktop"} size={18} />
        {m.label}
      </button>
      {open ? (
        <div className="dropdown-menu show" role="menu" aria-label={m.label} onKeyDown={onMenuKey}>
          {options.map((o) => (
            <button
              key={o.value}
              type="button"
              role="menuitemradio"
              tabIndex={-1}
              aria-checked={shown === o.value}
              className={`dropdown-item d-flex align-items-center gap-2${shown === o.value ? " active" : ""}`}
              onClick={() => {
                setOpen(false);
                onChoose(o.value);
                trigger.current?.focus();
              }}
            >
              <Icon name={o.icon} size={18} />
              {o.label}
              {shown === o.value ? (
                <span className="ms-auto">
                  <Icon name="check" size={16} />
                </span>
              ) : null}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
