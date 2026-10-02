import { type ReactNode, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { rememberDestination } from "../api/destination";
import { UnavailablePage } from "../pages/system/UnavailablePage";
import { BareSystemFrame } from "./layouts";
import { failureBus, type Retry } from "./queryClient";

/**
 * Routes sign-in and session-ended failures; shows SCR-93 over the page for the rest. The page stays mounted
 * (hidden) beneath SCR-93, so Retry re-runs the page action against live state and its outcome (navigate,
 * new grant) lands as if the first attempt had worked.
 */
export function FailureBoundary({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [pending, setPending] = useState<Retry | null>(null);
  const saved = useRef<Retry | null>(null);
  const failures = useRef(0);

  useEffect(() => {
    failureBus.handler = (failure, retry, source) => {
      if (failure.route === "sign-in") {
        saved.current = null;
        setPending(null);
        rememberDestination(location.pathname + location.search + location.hash);
        void navigate("/sign-in");
      } else if (failure.route === "session-ended") {
        saved.current = null;
        setPending(null);
        void navigate("/session-ended");
      } else if (source === "query" && saved.current) {
        // The page stays mounted under SCR-93; its background refetch failures must not replace the saved action.
      } else {
        failures.current += 1;
        saved.current = retry;
        setPending(() => retry);
      }
    };
  }, [location, navigate]);

  async function retry() {
    const action = saved.current;
    if (!action) return;
    const before = failures.current;
    await action();
    // A page action that fails again reports through the bus instead of throwing.
    if (failures.current !== before) throw new Error("still unavailable");
    saved.current = null;
    setPending(null);
  }

  return (
    <>
      <div hidden={pending !== null}>{children}</div>
      {pending ? (
        <BareSystemFrame>
          <UnavailablePage onRetry={retry} />
        </BareSystemFrame>
      ) : null}
    </>
  );
}
