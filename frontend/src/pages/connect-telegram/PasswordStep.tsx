import { useEffect, useId, useRef, useState } from "react";
import { ApiFailure } from "../../api/client";
import { submitLinkingPassword } from "../../api/linking";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { dispatchResult, isValidation, refusalText, type StepProps } from "./steps";
import { CancelButton } from "./CancelButton";

export function PasswordStep(props: StepProps) {
  const t = messages.linking;
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [hint, setHint] = useState(props.attempt.passwordHint);
  const [submitting, setSubmitting] = useState(false);
  const [focusSignal, setFocusSignal] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  const errorId = useId();

  useEffect(() => {
    if (focusSignal > 0) input.current?.focus();
  }, [focusSignal]);

  function reject(message: string) {
    setError(message);
    setPassword("");
    setFocusSignal((n) => n + 1);
  }

  async function submit() {
    if (!password) return reject(t.passwordRequired);
    setError(null);
    setSubmitting(true);
    try {
      dispatchResult(await submitLinkingPassword(password), props);
    } catch (e) {
      setSubmitting(false);
      const refused = refusalText(e);
      if (refused) {
        if (e instanceof ApiFailure && e.extras.passwordHint) setHint(e.extras.passwordHint);
        reject(refused);
      } else if (isValidation(e)) reject(t.passwordRequired);
      else {
        setPassword("");
        props.onCommonFailure(e, () => void submit());
      }
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
      <p className="text-secondary">{t.passwordIntro}</p>
      <div className="mb-3">
        <label className="form-label" htmlFor="linking-password">
          {t.passwordLabel}
        </label>
        <input
          id="linking-password"
          ref={input}
          type="password"
          autoComplete="off"
          className={`form-control${error ? " is-invalid" : ""}`}
          value={password}
          readOnly={submitting}
          aria-invalid={error !== null}
          aria-describedby={error ? errorId : undefined}
          onChange={(e) => setPassword(e.target.value)}
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
        ) : null}
        {hint ? (
          <small className="form-hint d-flex align-items-center gap-1 mt-1">
            <Icon name="info-circle" size={16} />
            {t.passwordHint(hint)}
          </small>
        ) : null}
      </div>
      <p className={error ? "mb-3" : "text-secondary small mb-3"}>{t.passwordForgot}</p>
      <div className="d-grid gap-2">
        <Button type="submit" busy={submitting}>
          {submitting ? t.checkingPassword : t.continue}
        </Button>
        <CancelButton disabled={submitting} onCancel={props.onCancel} />
      </div>
    </form>
  );
}
