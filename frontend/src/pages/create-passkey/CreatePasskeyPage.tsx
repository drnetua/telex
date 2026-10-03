import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { canCreatePasskey, createPasskey, PasskeyCancelled } from "../../api/webauthn";
import { Button } from "../../components/Button/Button";
import { Icon } from "../../components/Icon/Icon";
import { messages } from "../../messages";
import { takeRememberedDestination } from "../../api/destination";
import { routeFailure } from "../auth/failure";

type Step = "checking" | "default" | "unsupported" | "waiting" | "failed";

export function CreatePasskeyPage() {
  const navigate = useNavigate();
  const flagged = (useLocation().state as { createdAccount?: boolean } | null)?.createdAccount;
  const [step, setStep] = useState<Step>("checking");
  const m = messages.createPasskey;

  useEffect(() => {
    if (!flagged) return;
    let live = true;
    void canCreatePasskey().then((ok) => {
      if (live) setStep(ok ? "default" : "unsupported");
    });
    return () => {
      live = false;
    };
  }, [flagged]);

  // Reloaded or opened directly: consume the remembered section once, in an effect (render must stay pure, and
  // a render React discards would otherwise lose it).
  const reloaded = useRef(false);
  useEffect(() => {
    if (flagged || reloaded.current) return;
    reloaded.current = true;
    void navigate(takeRememberedDestination(), { replace: true });
  }, [flagged, navigate]);
  if (!flagged) return null;

  const leave = () => void navigate(takeRememberedDestination(), { replace: true });

  async function create() {
    setStep("waiting");
    try {
      await createPasskey();
      leave();
    } catch (error) {
      setStep("failed");
      if (error instanceof PasskeyCancelled) return;
      if (error instanceof ApiFailure && error.code === "passkey-registration-failed") return;
      if (!routeFailure(error, create)) throw error;
    }
  }

  if (step === "checking") return null;

  if (step === "unsupported") {
    return (
      <div className="text-center">
        <Icon name="info-circle" />
        <h1 className="h2 mt-2">{m.unsupportedTitle}</h1>
        <p className="text-secondary">{m.unsupportedBody}</p>
        <Button className="btn-primary w-100" onClick={leave}>
          {m.continue}
        </Button>
      </div>
    );
  }

  const waiting = step === "waiting";
  return (
    <div className="text-center">
      <Icon name="lock" />
      <h1 className="h2 mt-2">{m.title}</h1>
      <p className="text-secondary">{m.body}</p>
      {step === "failed" ? (
        <div className="alert alert-danger d-flex align-items-center gap-2 text-start" role="alert">
          <Icon name="alert-circle" size={18} />
          {m.failed}
        </div>
      ) : null}
      <div className="d-grid gap-2">
        <Button busy={waiting} onClick={() => void create()}>
          {waiting ? m.waiting : step === "failed" ? m.retry : m.create}
        </Button>
        <Button className="btn-ghost-secondary" disabled={waiting} onClick={leave}>
          {m.notNow}
        </Button>
      </div>
    </div>
  );
}
