import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router";

interface Arrival {
  message: string;
  tone?: "info" | "error";
}

/** The Toast a page was navigated to with; read once, then cleared so reload and Back don't repeat it. */
export function useArrivalToast() {
  const state = useLocation().state as { toast?: string; tone?: "info" | "error" } | null;
  const navigate = useNavigate();
  const [arrival, setArrival] = useState<Arrival | null>(() =>
    state?.toast ? { message: state.toast, tone: state.tone } : null,
  );
  const hasToast = Boolean(state?.toast);
  useEffect(() => {
    if (hasToast) void navigate(".", { replace: true, state: null });
  }, [hasToast, navigate]);
  const dismiss = useCallback(() => setArrival(null), []);
  return { arrival, dismiss };
}
