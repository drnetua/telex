import { type ClipboardEvent, type KeyboardEvent, useEffect, useId, useRef } from "react";
import { messages } from "../../messages";

const LENGTH = 6;

interface CodeInputProps {
  value: string;
  onChange: (value: string) => void;
  state?: "input" | "invalid";
  /** Increment to move focus to the first digit. */
  focusSignal?: number;
  /** Id of the element that describes the error; set on every digit while `state="invalid"`. */
  describedBy?: string;
}

export function CodeInput({
  value,
  onChange,
  state = "input",
  focusSignal = 0,
  describedBy,
}: CodeInputProps) {
  const labelId = useId();
  const refs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (focusSignal > 0) refs.current[0]?.focus();
  }, [focusSignal]);

  function fill(index: number, raw: string) {
    const digits = raw.replace(/\D/g, "");
    if (!digits) return;
    const next = (value.slice(0, index) + digits).slice(0, LENGTH);
    onChange(next);
    refs.current[Math.min(next.length, LENGTH - 1)]?.focus();
  }

  function onPaste(index: number, e: ClipboardEvent<HTMLInputElement>) {
    e.preventDefault();
    fill(index, e.clipboardData.getData("text"));
  }

  function onKeyDown(index: number, e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Backspace" && !value[index] && index > 0) {
      onChange(value.slice(0, index - 1));
      refs.current[index - 1]?.focus();
    }
  }

  return (
    <>
      <div id={labelId} className="form-label text-center">
        {messages.checkEmail.codeLabel}
      </div>
      <div className="d-flex gap-2 justify-content-center" role="group" aria-labelledby={labelId}>
        {Array.from({ length: LENGTH }, (_, i) => (
          <input
            key={i}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="text"
            inputMode="numeric"
            autoComplete={i === 0 ? "one-time-code" : "off"}
            maxLength={i === 0 ? LENGTH : 1}
            aria-label={messages.checkEmail.digitLabel(i + 1)}
            aria-invalid={state === "invalid"}
            aria-describedby={state === "invalid" ? describedBy : undefined}
            className={`form-control text-center${state === "invalid" ? " is-invalid" : ""}`}
            style={{ width: "3rem" }}
            value={value[i] ?? ""}
            onChange={(e) => {
              if (e.target.value === "") onChange(value.slice(0, i) + value.slice(i + 1));
              else fill(i, e.target.value);
            }}
            onPaste={(e) => onPaste(i, e)}
            onKeyDown={(e) => onKeyDown(i, e)}
          />
        ))}
      </div>
    </>
  );
}
