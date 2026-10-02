const KEY = "telex.destination";
const INBOX = "/inbox";
const AUTH_PAGES = ["/sign-in", "/welcome", "/session-ended"];

function isAuthPage(path: string): boolean {
  const pathname = path.split(/[?#]/)[0] ?? "";
  return AUTH_PAGES.some((page) => pathname === page || pathname.startsWith(`${page}/`));
}

/**
 * Remember where a signed-out person was headed, in the browser that asked to sign in.
 * Kept once (the first page that was refused wins) and never a sign-in or other auth page.
 */
export function rememberDestination(path: string): void {
  if (isAuthPage(path) || localStorage.getItem(KEY) !== null) return;
  localStorage.setItem(KEY, path);
}
/** Single-slash relative paths only; anything else leads to the Inbox. Cleared on read. */
export function takeRememberedDestination(): string {
  const path = localStorage.getItem(KEY);
  localStorage.removeItem(KEY);
  if (path && path.startsWith("/") && !path.startsWith("//") && !path.includes("\\")) return path;
  return INBOX;
}
