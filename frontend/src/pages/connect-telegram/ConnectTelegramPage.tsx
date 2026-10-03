import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";
import { useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import {
  formatMaskedPhone,
  listMyLinkedAccounts,
  useLinkedAccounts,
} from "../../api/linkedAccounts";
import {
  cancelMyLinkingAttempt,
  getMyLinkingAttempt,
  linkingAttemptKey,
  type LinkingAttempt,
  type LinkingOrigin,
  startMyLinkingAttempt,
  useLinkingAttempt,
} from "../../api/linking";
import { LoadState } from "../../components/LoadState/LoadState";
import { Toast } from "../../components/Toast/Toast";
import { messages } from "../../messages";
import { routeFailure } from "../auth/failure";
import { CodeStep } from "./CodeStep";
import { PasswordStep } from "./PasswordStep";
import { PhoneStep } from "./PhoneStep";
import { AttemptEndedState, RefusedState, WaitState } from "./Outcomes";
import { REFUSAL_CODES, type Outcome, type RefusalCode } from "./outcome";
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
  const accounts = useLinkedAccounts();
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [starting, setStarting] = useState(false);
  const attempt = query.data;
  const [last, setLast] = useState<LinkingAttempt | undefined>(undefined);
  const known = attempt ?? last;

  const show = (message: string, tone: Notice["tone"]) => {
    setNotice(null);
    setTimeout(() => setNotice({ message, tone }), 0);
  };

  const leave = (origin: LinkingOrigin, toast?: string) =>
    void navigate(origin === "accounts" ? "/accounts" : "/inbox", {
      replace: true,
      state: toast ? { toast } : undefined,
    });
  const origin = (): LinkingOrigin => known?.origin ?? "inbox";

  const finished = async (result: FinishedResult) => {
    let toast: string | undefined;
    try {
      const linked = (await listMyLinkedAccounts()).find((a) => a.id === result.linkedAccountId);
      if (linked) {
        toast =
          result.outcome === "linked"
            ? messages.linking.connected(linked.displayName)
            : messages.linking.connectedAgain(linked.displayName);
      }
    } catch {
      // the account is linked either way; the toast is only a courtesy
    }
    leave(result.origin, toast);
  };

  const startAgain = async () => {
    const previous = known;
    setStarting(true);
    try {
      const fresh = await startMyLinkingAttempt({
        origin: origin(),
        ...(previous?.targetLinkedAccountId
          ? { targetLinkedAccountId: previous.targetLinkedAccountId }
          : {}),
      });
      client.setQueryData(linkingAttemptKey, fresh);
      setOutcome(null);
    } catch (error) {
      const problem = error instanceof ApiFailure ? error : undefined;
      const text = problem ? startRefusal(problem) : undefined;
      if (text) show(text, "error");
      else if (!routeFailure(error, async () => startAgain())) throw error;
    } finally {
      setStarting(false);
    }
  };

  /** Ends the wizard on the outcome a 429, 409 or 404 stands for; false for anything else. */
  const outcomeOf = (error: unknown): boolean => {
    if (!(error instanceof ApiFailure)) return false;
    setLast(attempt);
    if (error.status === 429 && error.code === "telegram-wait-required" && error.extras.retryAt) {
      setOutcome({ kind: "wait", retryAt: error.extras.retryAt });
    } else if (error.status === 409 && REFUSAL_CODES.includes(error.code)) {
      setOutcome({ kind: "refused", code: error.code as RefusalCode, limit: error.extras.limit });
    } else if (error.status === 404 && error.code === "linking-attempt-not-found") {
      setOutcome({ kind: "ended" });
    } else return false;
    return true;
  };

  const common = (error: unknown, retry: () => void): boolean => {
    if (outcomeOf(error)) return true;
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
    leave(origin());
  };

  const ended =
    query.error instanceof ApiFailure &&
    query.error.status === 404 &&
    query.error.code === "linking-attempt-not-found";
  const shownOutcome: Outcome | null = outcome ?? (ended && !attempt ? { kind: "ended" } : null);
  if (query.error && !ended && !outcome) throw query.error;
  if (shownOutcome) {
    const back = () => leave(origin());
    const target = known?.targetLinkedAccountId;
    return (
      <Card>
        {shownOutcome.kind === "wait" ? (
          <WaitState
            retryAt={shownOutcome.retryAt}
            starting={starting}
            onBack={back}
            onStartAgain={() => void startAgain()}
          />
        ) : null}
        {shownOutcome.kind === "refused" ? (
          <RefusedState
            code={shownOutcome.code}
            limit={shownOutcome.limit}
            displayName={accounts.data?.find((a) => a.id === target)?.displayName}
            onBack={back}
            onOpenAccounts={() => leave("accounts")}
          />
        ) : null}
        {shownOutcome.kind === "ended" ? (
          <AttemptEndedState
            starting={starting}
            onBack={back}
            onStartAgain={() => void startAgain()}
          />
        ) : null}
        {notice ? <Toast message={notice.message} tone={notice.tone} onDismiss={dismiss} /> : null}
      </Card>
    );
  }
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
    onFinished: (result: FinishedResult) => void finished(result),
    onCommonFailure: common,
    onInfo: (message: string) => show(message, "info"),
    onCancel: cancel,
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

function startRefusal(error: ApiFailure): string | undefined {
  const problems: Record<string, unknown> = messages.linking.problems;
  const text = problems[error.code];
  if (typeof text === "function")
    return (text as (limit: number) => string)(error.extras.limit ?? 0);
  return typeof text === "string" ? text : undefined;
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
