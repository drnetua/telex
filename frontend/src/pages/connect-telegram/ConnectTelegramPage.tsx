import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { ApiFailure } from "../../api/client";
import { refusalFor } from "../../api/linkingRefusal";
import {
  formatMaskedPhone,
  linkedAccountsKey,
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
import { endsAttempt, type FinishedResult } from "./steps";

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
  // The load-failure Toast is tracked apart from `notice`: it belongs to the query's error, not to a step.
  const [dismissedLoadError, setDismissedLoadError] = useState(0);
  const dismissLoadError = useCallback(
    () => setDismissedLoadError(query.errorUpdatedAt),
    [query.errorUpdatedAt],
  );
  const accounts = useLinkedAccounts();
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [starting, setStarting] = useState(false);
  const attempt = query.data;
  const [last, setLast] = useState<LinkingAttempt | undefined>(undefined);
  const known = attempt ?? last;

  // A fresh attempt put in the cache from outside (the Status Banner's Sign in again) replaces any outcome card.
  // Structural sharing keeps the old reference for an equal attempt, so listen for the write itself.
  useEffect(
    () =>
      client.getQueryCache().subscribe((event) => {
        if (
          event.type === "updated" &&
          event.action.type === "success" &&
          event.action.manual &&
          event.query.queryKey[0] === linkingAttemptKey[0] &&
          event.action.data
        )
          setOutcome(null);
      }),
    [client],
  );

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
      const list = await listMyLinkedAccounts();
      // The page we leave to renders this list on first paint, not the pre-link one.
      client.setQueryData(linkedAccountsKey, list);
      const linked = list.find((a) => a.id === result.linkedAccountId);
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
      const text = refusalFor(error);
      if (text) show(text, "error");
      else if (!routeFailure(error, async () => startAgain())) {
        show(messages.linking.genericError, "error");
      }
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
    } else if (endsAttempt(error)) {
      setOutcome({ kind: "ended" });
    } else if (error.status === 404 && error.code === "linking-attempt-not-found") {
      setOutcome({ kind: "ended" });
    } else return false;
    return true;
  };

  const common = (error: unknown, retry: () => void): void => {
    if (outcomeOf(error)) return;
    if (error instanceof ApiFailure && error.code === "telegram-unavailable") {
      show(messages.linking.problems["telegram-unavailable"], "error");
    } else if (error instanceof ApiFailure && error.code === "linking-step-mismatch") {
      getMyLinkingAttempt()
        .then((fresh) => {
          client.setQueryData(linkingAttemptKey, fresh);
          show(messages.linking.stepDone, "info");
        })
        .catch((e: unknown) => common(e, retry));
    } else if (!routeFailure(error, async () => retry())) {
      show(messages.linking.genericError, "error");
    }
  };

  const cancel = async () => {
    try {
      await cancelMyLinkingAttempt();
    } catch (error) {
      common(error, () => void cancel());
      return;
    }
    leave(origin());
  };

  const ended =
    query.error instanceof ApiFailure &&
    query.error.status === 404 &&
    query.error.code === "linking-attempt-not-found";
  const shownOutcome: Outcome | null = outcome ?? (ended && !attempt ? { kind: "ended" } : null);
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
    // A failed load: routable failures reach the failure bus, others get a Toast with Retry.
    const loadFailed = query.error && !(query.error instanceof ApiFailure && query.error.route);
    return (
      <Card>
        <LoadState state="loading" rows={3} busy={!loadFailed || query.isFetching} />
        {loadFailed && !query.isFetching && dismissedLoadError !== query.errorUpdatedAt ? (
          <Toast
            message={messages.linking.loadFailed}
            tone="error"
            onDismiss={dismissLoadError}
            action={{ label: messages.linking.tryAgain, onClick: () => void query.refetch() }}
          />
        ) : null}
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

function Card({ children }: { children: React.ReactNode }) {
  // The card itself comes from OnboardingLayout.
  return <>{children}</>;
}
