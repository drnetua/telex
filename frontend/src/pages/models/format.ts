import { messages } from "../../messages";

/** "3 hours ago" style, in the catalog's own words. */
export function relativeTime(iso: string, now = Date.now()): string {
  const m = messages.models;
  const minutes = Math.floor(Math.max(0, now - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return m.justNow;
  if (minutes < 60) return m.minutesAgo(minutes);
  if (minutes < 1440) return m.hoursAgo(Math.floor(minutes / 60));
  return m.daysAgo(Math.floor(minutes / 1440));
}

/** "2 Oct, 06:00" — absolute, for the update-failed note. */
export function shortDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-GB", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

/** Full absolute time for tooltips. */
export function fullDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" });
}
