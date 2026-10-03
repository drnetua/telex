import { messages } from "../messages";

const MAX = 99;
const m = messages.shell.inboxCounter;

const labelFor = (count: number) => (count > MAX ? m.labelMax : m.label(count));

/** The count pill; nothing at 0 or before the first pulse answers. */
export function InboxPill({ count }: { count?: number }) {
  if (!count || count < 1) return null;
  return (
    <span className="badge bg-primary ms-1" aria-label={labelFor(count)}>
      {count > MAX ? m.max : count}
    </span>
  );
}

/** Polite live region: its text is derived from the count, so it speaks once per change, not per pulse. */
export function InboxAnnouncement({ count }: { count?: number }) {
  return (
    <span role="status" aria-live="polite" className="visually-hidden">
      {count === undefined ? "" : count < 1 ? m.none : labelFor(count)}
    </span>
  );
}

export function InboxCounter({ count }: { count?: number }) {
  return (
    <>
      <InboxPill count={count} />
      <InboxAnnouncement count={count} />
    </>
  );
}
