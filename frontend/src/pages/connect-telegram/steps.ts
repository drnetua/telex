import { ApiFailure } from "../../api/client";
import type { LinkingAttempt, LinkingStepResult } from "../../api/linking";
import { messages } from "../../messages";

export type FinishedResult = Extract<LinkingStepResult, { outcome: "linked" | "signed-in-again" }>;

export interface StepProps {
  attempt: LinkingAttempt;
  /** Applies the step the server moved to. */
  onNext: (attempt: LinkingAttempt) => void;
  /** The attempt ended in a linked account (T21 renders the outcome). */
  onFinished: (result: FinishedResult) => void;
  /** Shared failures (503, 409 mismatch, routable ones); true when it handled the error. */
  onCommonFailure: (error: unknown, retry: () => void) => boolean;
  onInfo: (message: string) => void;
  onCancel: () => void;
}

/** Message of a 422 refusal for its problem code, when the catalog has one. */
export function refusalText(error: unknown): string | null {
  if (!(error instanceof ApiFailure) || error.status !== 422) return null;
  const known: Record<string, unknown> = messages.linking.problems;
  const text = known[error.code];
  return typeof text === "string" ? text : null;
}

export function isValidation(error: unknown): boolean {
  return error instanceof ApiFailure && error.status === 400;
}

export function dispatchResult(result: LinkingStepResult, props: StepProps): void {
  if (result.outcome === "next") props.onNext(result.attempt);
  else props.onFinished(result);
}
