import { messages } from "../messages";
import { ApiFailure } from "./client";

/** The refusal text for a failed start; `undefined` when the failure is silent or shared. */
export function refusalFor(error: unknown): string | undefined {
  if (!(error instanceof ApiFailure)) return undefined;
  const problems: Record<string, unknown> = messages.linking.problems;
  const text = problems[error.code];
  if (typeof text === "function")
    return (text as (limit: number) => string)(error.extras.limit ?? 0);
  return typeof text === "string" ? text : undefined;
}
