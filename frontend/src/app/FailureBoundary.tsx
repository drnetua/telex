import { type ReactNode, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { rememberDestination } from "../api/destination";
import { UnavailablePage } from "../pages/system/UnavailablePage";
import { failureBus, type Retry } from "./queryClient";

/** Routes sign-in and session-ended failures; renders SCR-93 in place of the page for the rest. */
export function FailureBoundary({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [pending, setPending] = useState<Retry | null>(null);

  useEffect(() => {
    failureBus.handler = (failure, retry) => {
      if (failure.route === "sign-in") {
        rememberDestination(location.pathname + location.search + location.hash);
        void navigate("/sign-in");
      } else if (failure.route === "session-ended") {
        void navigate("/session-ended");
      } else {
        setPending(() => retry);
      }
    };
  }, [location, navigate]);

  if (pending) {
    return (
      <UnavailablePage
        onRetry={async () => {
          await pending();
          setPending(null);
        }}
      />
    );
  }
  return children;
}
