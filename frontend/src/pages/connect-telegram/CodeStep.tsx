import { useId, useState } from "react";
import { ApiFailure } from "../../api/client";
import { resendLinkingCode, submitLinkingCode } from "../../api/linking";
import { Button } from "../../components/Button/Button";
import { CodeInput } from "../../components/CodeInput/CodeInput";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { dispatchResult, isValidation, refusalText, type StepProps } from "./steps";
import { CancelButton } from "./CancelButton";

const DEFAULT_CODE_LENGTH = 5;

export function CodeStep(props: StepProps) {
  const t = messages.linking;
  const length = props.attempt.codeLength ?? DEFAULT_CODE_LENGTH;
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const [focusSignal, setFocusSignal] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const errorId = useId();
  const busy = submitting || resending;

  function reject(message: string, clear: boolean) {
    setError(message);
    if (clear) setCode("");
    setFocusSignal((n) => n + 1);
  }

  async function submit() {
    if (code.length < length) return reject(t.codeIncomplete(length), false);
    setError(null);
    setSubmitting(true);
    try {
      dispatchResult(await submitLinkingCode(code), props);
    } catch (e) {
      setSubmitting(false);
      const refused = refusalText(e);
      if (refused) {
        setExpired(e instanceof ApiFailure && e.code === "telegram-code-expired");
        reject(refused, true);
      } else if (isValidation(e)) reject(t.codeIncomplete(length), false);
      else if (!props.onCommonFailure(e, () => void submit())) throw e;
    }
  }

  async function resend() {
    setResending(true);
    try {
      const next = await resendLinkingCode();
      setCode("");
      setError(null);
      setExpired(false);
      props.onInfo(t.codeResent);
      props.onNext(next);
    } catch (e) {
      if (!props.onCommonFailure(e, () => void resend())) throw e;
    } finally {
      setResending(false);
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
      <p className="text-secondary">{t.codeIntro}</p>
      <div className="mb-2">
        <CodeInput
          value={code}
          onChange={setCode}
          length={length}
          label={t.codeLabel}
          state={error ? "invalid" : "input"}
          focusSignal={focusSignal}
          describedBy={errorId}
          readOnly={busy}
        />
        {error ? (
          <div
            id={errorId}
            role="alert"
            className="invalid-feedback d-flex align-items-center gap-1 justify-content-center"
          >
            <Icon name="alert-circle" size={16} />
            {error}
          </div>
        ) : null}
      </div>
      <div className="d-grid gap-2 mt-3">
        <Button
          type="submit"
          busy={submitting}
          className={expired ? "btn-ghost-secondary" : "btn-primary"}
          disabled={resending}
        >
          {submitting ? t.checkingCode : t.continue}
        </Button>
        <Button
          className={expired ? "btn-primary" : "btn-ghost-secondary"}
          busy={resending}
          disabled={submitting}
          onClick={() => void resend()}
        >
          {resending ? t.sendingNewCode : t.sendNewCode}
        </Button>
        <CancelButton disabled={busy} onCancel={props.onCancel} />
      </div>
    </form>
  );
}
