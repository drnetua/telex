import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { formatMaskedPhone, useLinkedAccounts } from "../../api/linkedAccounts";
import {
  cancelMyLinkingAttempt,
  getMyLinkingAttempt,
  linkingAttemptKey,
  type LinkingAttempt,
  useLinkingAttempt,
} from "../../api/linking";
import { LoadState } from "../../components/LoadState/LoadState";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";
import { CodeStep } from "./CodeStep";
import { PasswordStep } from "./PasswordStep";
import { PhoneStep } from "./PhoneStep";
import type { FinishedResult } from "./steps";

interface Notice {
  message: string;
  tone: "info" | "error";
}

function Title({ attempt }: { attempt: LinkingAttempt }) {
  const accounts = useLinkedAccounts();
  const target = attempt.targetLinkedAccountId
    ? accounts.data?.find((a) => a.id === attempt.targetLinkedAccountId)
    : undefined;
  return (
    <h1 className="h2 text-center">
      {target
        ? messages.linking.titleAgain(target.displayName, formatMaskedPhone(target.phone))
        : messages.linking.title}
    </h1>
  );
}

export function ConnectTelegramPage() {
  const query = useLinkingAttempt();
  const client = useQueryClient();
  const navigate = useNavigate();
  const [notice, setNotice] = useState<Notice | null>(null);
  const dismiss = useCallback(() => setNotice(null), []);
  const attempt = query.data;

  const show = (message: string, tone: Notice["tone"]) => {
    setNotice(null);
    setTimeout(() => setNotice({ message, tone }), 0);
  };

  const leave = (origin: LinkingAttempt["origin"]) =>
    void navigate(origin === "accounts" ? "/accounts" : "/inbox", { replace: true });

  const common = (error: unknown, retry: () => void): boolean => {
    if (error instanceof ApiFailure && error.code === "telegram-unavailable") {
      show(messages.linking.problems["telegram-unavailable"], "error");
      return true;
    }
    if (error instanceof ApiFailure && error.code === "linking-step-mismatch") {
      void getMyLinkingAttempt().then((fresh) => {
        client.setQueryData(linkingAttemptKey, fresh);
        show(messages.linking.stepDone, "info");
      });
      return true;
    }
    return routeFailure(error, async () => retry());
  };

  const cancel = async () => {
    try {
      await cancelMyLinkingAttempt();
    } catch (error) {
      if (!common(error, () => void cancel())) throw error;
      return;
    }
    leave(attempt?.origin ?? "inbox");
  };

  if (query.error instanceof ApiFailure && query.error.status === 404) {
    return (
      <Card>
        <h1 className="h2 text-center">{messages.linking.endedTitle}</h1>
        <p className="text-secondary">{messages.linking.endedBody}</p>
      </Card>
    );
  }
  if (query.error) throw query.error;
  if (!attempt) {
    return (
      <Card>
        <LoadState state="loading" rows={3} />
      </Card>
    );
  }

  const props = {
    attempt,
    onNext: (next: LinkingAttempt) => client.setQueryData(linkingAttemptKey, next),
    onFinished: (result: FinishedResult) => leave(result.origin),
    onCommonFailure: common,
    onInfo: (message: string) => show(message, "info"),
    onCancel: () => void cancel(),
  };

  return (
    <Card>
      <Title attempt={attempt} />
      {attempt.step === "phone" ? <PhoneStep key="phone" {...props} /> : null}
      {attempt.step === "code" ? <CodeStep key="code" {...props} /> : null}
      {attempt.step === "password" ? <PasswordStep key="password" {...props} /> : null}
      {notice ? <Toast message={notice.message} tone={notice.tone} onDismiss={dismiss} /> : null}
    </Card>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="container-tight py-4">
      <div className="card card-md">
        <div className="card-body">{children}</div>
      </div>
    </div>
  );
}
