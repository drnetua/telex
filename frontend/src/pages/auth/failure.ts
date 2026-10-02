import { ApiFailure } from "../../api/client";
import { failureBus } from "../../app/queryClient";

/** Hands routable failures (403, 5xx, network) to the shared failure routing; returns true when handled. */
export function routeFailure(error: unknown, retry: () => Promise<unknown>): boolean {
  if (error instanceof ApiFailure && error.route) {
    failureBus.handler(error, retry);
    return true;
  }
  return false;
}
