import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { previewSignInLink, redeemSignInLink, requestSignInEmail } from "../../api/signIn";
import { landAfterSignIn } from "../../app/landing";
import { Button } from "../../components/Button/Button";
import { EmptyState } from "../../components/EmptyState/EmptyState";
import { LoadState } from "../../components/LoadState/LoadState";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";
import { RefusalCard } from "../auth/RefusalCard";

const REFUSALS = {
  "sign-in-link-expired": { icon: "clock", title: "expiredTitle", body: "refusalBody" },
  "sign-in-link-used": { icon: "ban", title: "usedTitle", body: "refusalBody" },
  "sign-in-grant-void": { icon: "ban", title: "voidTitle", body: "voidBody" },
} as const;

type RefusalCode = keyof typeof REFUSALS;

interface Refused {
  code: RefusalCode;
  email: string;
}

function refusedFrom(error: unknown): Refused | null {
  if (error instanceof ApiFailure && error.status === 410 && error.email && error.code in REFUSALS)
    return { code: error.code as RefusalCode, email: error.email };
  return null;
}

export function ConfirmLinkPage() {
  const navigate = useNavigate();
  const token = useLocation().hash.replace(/^#/, "");
  const [refused, setRefused] = useState<Refused | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const m = messages.confirmLink;

  const preview = useQuery({
    queryKey: ["sign-in-link-preview", token],
    queryFn: () => previewSignInLink(token),
    enabled: token !== "",
    retry: false,
    staleTime: Infinity,
  });

  async function confirm() {
    setSubmitting(true);
    try {
      const result = await redeemSignInLink(token);
      void navigate(landAfterSignIn(result.createdAccount), { replace: true });
    } catch (error) {
      setSubmitting(false);
      const found = refusedFrom(error);
      if (found) setRefused(found);
      else if (error instanceof ApiFailure && (error.status === 410 || error.status === 400))
        setRefused(null);
      else if (!routeFailure(error, confirm)) throw error;
    }
  }

  async function resend(email: string) {
    setResending(true);
    try {
      const next = await requestSignInEmail(email);
      void navigate("/sign-in/check-email", {
        state: { grantId: next.grantId, email: next.email },
      });
    } catch (error) {
      setResending(false);
      if (!routeFailure(error, () => resend(email))) throw error;
    }
  }

  const unusable = (
    <EmptyState
      kind="blocked"
      icon="ban"
      title={m.unusableTitle}
      action={<Button onClick={() => void navigate("/sign-in")}>{m.back}</Button>}
    >
      {m.unusableBody}
    </EmptyState>
  );

  const refusal = refused ?? refusedFrom(preview.error);
  if (refusal) {
    const spec = REFUSALS[refusal.code];
    const c = messages.checkEmail;
    return (
      <RefusalCard
        icon={spec.icon}
        title={c[spec.title]}
        body={c[spec.body](refusal.email)}
        busy={resending}
        onResend={() => void resend(refusal.email)}
      />
    );
  }

  if (token === "") return unusable;
  if (preview.isError) {
    const error = preview.error;
    if (error instanceof ApiFailure && (error.status === 410 || error.status === 400))
      return unusable;
    if (!routeFailure(error, () => preview.refetch())) throw error;
    return <LoadState state="loading" rows={2} />;
  }
  if (preview.isPending) return <LoadState state="loading" rows={2} />;

  const email = preview.data.email;
  return (
    <div>
      <h1 className="h2 text-center">{m.title}</h1>
      <p className="text-secondary text-center">{m.body}</p>
      <div className="d-grid mt-3">
        <Button busy={submitting} onClick={() => void confirm()}>
          {submitting ? m.submitting : m.continueAs(email)}
        </Button>
      </div>
      <small className="text-secondary d-block text-center mt-3">{m.note}</small>
    </div>
  );
}
