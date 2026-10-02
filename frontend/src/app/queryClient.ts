import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { ApiFailure } from "../api/client";

export type Retry = () => Promise<unknown>;
/** "query" failures come from background refetches; "action" failures from something the Owner did. */
type Handler = (failure: ApiFailure, retry: Retry, source?: "query" | "action") => void;

/** Single subscriber (the FailureBoundary) that reacts to routable failures. */
export const failureBus: { handler: Handler } = { handler: () => undefined };

/** Query client whose caches report routable failures to the failure bus. */
export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (error instanceof ApiFailure && error.route) {
          failureBus.handler(error, () => query.fetch(), "query");
        }
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, variables, _ctx, mutation) => {
        if (error instanceof ApiFailure && error.route) {
          failureBus.handler(error, () => mutation.execute(variables));
        }
      },
    }),
  });
}
