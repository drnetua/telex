import { messages } from "../../messages";
import { formatInstant } from "../../shell/time";

const DAY_MS = 86_400_000;

export function formatDate(iso: string, timeZone: string | null): string {
  return formatInstant(iso, timeZone);
}

/** Relative under a day, a date in the Owner's zone after. */
export function formatWhen(iso: string, timeZone: string | null, now = Date.now()): string {
  const m = messages.profileSecurity;
  const diff = Math.max(0, now - new Date(iso).getTime());
  if (diff >= DAY_MS) return formatDate(iso, timeZone);
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return m.justNow;
  if (minutes < 60) return m.minutesAgo(minutes);
  return m.hoursAgo(Math.floor(minutes / 60));
}
