import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { redeemSignInCode, requestSignInEmail } from "../../api/signIn";
import { landAfterSignIn } from "../../app/landing";
import { Button } from "../../components/Button/Button";
import { CodeInput } from "../../components/CodeInput/CodeInput";
import type { IconName } from "../../components/Icon/Icon";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";
import { RefusalCard } from "../auth/RefusalCard";

interface GrantState {
  grantId: string;
  email: string;
}

type Refusal = { icon: IconName; title: string; body: string };

function refusalFor(code: string, email: string): Refusal | null {
  const m = messages.checkEmail;
  if (code === "sign-in-link-expired")
    return { icon: "clock", title: m.expiredTitle, body: m.refusalBody(email) };
  if (code === "sign-in-link-used")
    return { icon: "ban", title: m.usedTitle, body: m.refusalBody(email) };
  if (code === "sign-in-grant-void")
    return { icon: "ban", title: m.voidTitle, body: m.voidBody(email) };
  return null;
}

export function CheckEmailPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const initial = location.state as GrantState | null;
  const [grant, setGrant] = useState<GrantState | null>(initial?.grantId ? initial : null);
  const [code, setCode] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [wrong, setWrong] = useState(false);
  const [focusSignal, setFocusSignal] = useState(0);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);

  if (!grant) return <Navigate to="/sign-in" replace />;
  const m = messages.checkEmail;

  async function submit() {
    if (!grant) return;
    if (!/^\d{6}$/.test(code)) {
      setWrong(false);
      setMessage(m.codeInvalid);
      return;
    }
    setMessage(null);
    setWrong(false);
    setSubmitting(true);
    try {
      const result = await redeemSignInCode(grant.grantId, code);
      void navigate(landAfterSignIn(result.createdAccount), {
        replace: true,
        state: { createdAccount: result.createdAccount },
      });
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiFailure && error.status === 422) {
        const left = error.attemptsLeft ?? 0;
        setWrong(true);
        setMessage(m.wrongCode(left));
        setFocusSignal((n) => n + 1);
      } else if (error instanceof ApiFailure && error.status === 410) {
        const found = refusalFor(error.code, error.email ?? grant.email);
        if (!found) throw error;
        setRefusal(found);
      } else if (error instanceof ApiFailure && error.status === 400) {
        setMessage(m.codeInvalid);
      } else if (!routeFailure(error, submit)) throw error;
    }
  }

  async function resend() {
    if (!grant) return;
    setResending(true);
    try {
      const next = await requestSignInEmail(grant.email);
      setGrant({ grantId: next.grantId, email: next.email });
      setCode("");
      setMessage(null);
      setWrong(false);
      setRefusal(null);
      setResent(true);
    } catch (error) {
      if (!routeFailure(error, resend)) throw error;
    } finally {
      setResending(false);
    }
  }

  if (refusal) {
    return <RefusalCard {...refusal} busy={resending} onResend={() => void resend()} />;
  }

  return (
    <div>
      <h1 className="h2 text-center">{m.title}</h1>
      {resent ? (
        <div className="alert alert-info" role="status">
          {m.resent(grant.email)}
        </div>
      ) : null}
      <p className="text-secondary">{m.sent(grant.email)}</p>
      <div className="mb-2">
        <CodeInput
          value={code}
          onChange={setCode}
          state={wrong || message === m.codeInvalid ? "invalid" : "input"}
          focusSignal={focusSignal}
        />
        {message ? (
          <div className="invalid-feedback d-block d-flex align-items-center gap-1 justify-content-center">
            <Icon name="alert-circle" size={16} />
            {message}
          </div>
        ) : null}
      </div>
      <div className="d-grid gap-2 mt-3">
        <Button busy={submitting} onClick={() => void submit()}>
          {submitting ? m.submitting : m.submit}
        </Button>
        <Button className="btn-ghost-secondary" busy={resending} onClick={() => void resend()}>
          {resending ? m.resending : m.resend}
        </Button>
      </div>
      <small className="text-secondary d-block text-center mt-3">{m.note}</small>
    </div>
  );
}
