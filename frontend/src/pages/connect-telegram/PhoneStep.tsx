import { useEffect, useId, useRef, useState } from "react";
import { submitLinkingPhone } from "../../api/linking";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { dispatchResult, isValidation, refusalText, type StepProps } from "./steps";
import { CancelButton } from "./CancelButton";

export function PhoneStep(props: StepProps) {
  const t = messages.linking;
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [focusSignal, setFocusSignal] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const errorId = useId();

  useEffect(() => {
    if (focusSignal > 0) input.current?.focus();
  }, [focusSignal]);

  function reject(message: string) {
    setError(message);
    setFocusSignal((n) => n + 1);
  }

  async function submit() {
    const value = phone.trim();
    if (!value) return reject(t.phoneRequired);
    setError(null);
    setSubmitting(true);
    try {
      dispatchResult(await submitLinkingPhone(value), props);
    } catch (e) {
      setSubmitting(false);
      const refused = refusalText(e);
      if (refused) reject(refused);
      else if (isValidation(e)) reject(t.phoneRequired);
      else if (!props.onCommonFailure(e, () => void submit())) throw e;
    }
  }

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <p className="text-secondary">{t.intro}</p>
      <div className="mb-3">
        <label className="form-label" htmlFor="linking-phone">
          {t.phoneLabel}
        </label>
        <input
          id="linking-phone"
          ref={input}
          type="tel"
          autoComplete="tel"
          inputMode="tel"
          className={`form-control${error ? " is-invalid" : ""}`}
          placeholder={t.phonePlaceholder}
          value={phone}
          readOnly={submitting}
          aria-invalid={error !== null}
          aria-describedby={error ? errorId : undefined}
          onChange={(e) => setPhone(e.target.value)}
        />
        {error ? (
          <div
            id={errorId}
            role="alert"
            className="invalid-feedback d-flex align-items-center gap-1"
          >
            <Icon name="alert-circle" size={16} />
            {error}
          </div>
        ) : (
          <small className="form-hint">{t.phoneHint}</small>
        )}
      </div>
      <div className="d-grid gap-2">
        <Button type="submit" busy={submitting}>
          {submitting ? t.sendingCode : t.sendCode}
        </Button>
        <CancelButton disabled={submitting} onCancel={props.onCancel} />
      </div>
    </form>
  );
}
