import { type FormEvent, useId, useState } from "react";
import { useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { requestSignInEmail } from "../../api/signIn";
import { PasskeyCancelled, signInWithPasskey } from "../../api/webauthn";
import { landAfterSignIn } from "../../app/landing";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";

const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

export function SignInPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [invalid, setInvalid] = useState(false);
  const errorId = useId();
  const [submitting, setSubmitting] = useState(false);
  const [passkeyBusy, setPasskeyBusy] = useState(false);
  const [passkeyFailed, setPasskeyFailed] = useState(false);

  async function passkey() {
    setPasskeyFailed(false);
    setPasskeyBusy(true);
    try {
      const result = await signInWithPasskey();
      void navigate(landAfterSignIn(result.createdAccount), {
        replace: true,
        state: { createdAccount: result.createdAccount },
      });
    } catch (error) {
      setPasskeyBusy(false);
      if (error instanceof PasskeyCancelled) return;
      if (error instanceof ApiFailure && error.code === "passkey-rejected") setPasskeyFailed(true);
      else if (!routeFailure(error, passkey)) throw error;
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const address = email.trim();
    if (!EMAIL.test(address)) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    await send(address);
  }

  async function send(address: string) {
    setSubmitting(true);
    try {
      const grant = await requestSignInEmail(address);
      void navigate("/sign-in/check-email", {
        state: { grantId: grant.grantId, email: grant.email },
      });
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiFailure && error.status === 400) setInvalid(true);
      else if (!routeFailure(error, () => send(address))) throw error;
    }
  }

  return (
    <form noValidate onSubmit={(e) => void submit(e)}>
      <h1 className="h2 text-center">{messages.signIn.title}</h1>
      <p className="text-secondary text-center">{messages.signIn.tagline}</p>
      {passkeyFailed ? (
        <div className="alert alert-danger d-flex align-items-center gap-2" role="alert">
          <Icon name="alert-circle" size={18} />
          {messages.signIn.passkeyFailed}
        </div>
      ) : null}
      <div className="mb-3">
        <label className="form-label" htmlFor="sign-in-email">
          {messages.signIn.emailLabel}
        </label>
        <input
          id="sign-in-email"
          type="email"
          autoComplete="email"
          className={`form-control${invalid ? " is-invalid" : ""}`}
          value={email}
          readOnly={submitting}
          aria-invalid={invalid}
          aria-describedby={invalid ? errorId : undefined}
          onChange={(e) => setEmail(e.target.value)}
        />
        {invalid ? (
          <div
            id={errorId}
            role="alert"
            className="invalid-feedback d-flex align-items-center gap-1"
          >
            <Icon name="alert-circle" size={16} />
            {messages.signIn.emailInvalid}
          </div>
        ) : null}
      </div>
      <Button type="submit" className="btn-primary w-100" busy={submitting}>
        {submitting ? messages.signIn.submitting : messages.signIn.submit}
      </Button>
      <div className="hr-text">{messages.signIn.divider}</div>
      <Button
        icon="lock"
        className="btn-secondary w-100 mb-3"
        busy={passkeyBusy}
        disabled={submitting}
        onClick={() => void passkey()}
      >
        {passkeyBusy ? messages.signIn.passkeyWaiting : messages.signIn.passkey}
      </Button>
      <small className="text-secondary d-block text-center">{messages.signIn.note}</small>
    </form>
  );
}
