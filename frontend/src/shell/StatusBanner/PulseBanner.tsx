import { usePulse } from "../pulse";
import { StatusBanner } from "./StatusBanner";

/** The Status Banner fed by the pulse, for signed-in frames without the app shell (SCR-02, AC-122). */
export function PulseBanner() {
  const pulse = usePulse().data;
  return (
    <StatusBanner conditions={(pulse?.conditions ?? []).filter((c) => typeof c === "string")} />
  );
}
