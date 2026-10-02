import { messages } from "../../messages";

const DAY_MS = 86_400_000;

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/** Relative under a day, a date after. */
export function formatWhen(iso: string, now = Date.now()): string {
  const m = messages.profileSecurity;
  const diff = Math.max(0, now - new Date(iso).getTime());
  if (diff >= DAY_MS) return formatDate(iso);
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return m.justNow;
  if (minutes < 60) return m.minutesAgo(minutes);
  return m.hoursAgo(Math.floor(minutes / 60));
}
