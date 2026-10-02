const KEY = "telex.destination";
const INBOX = "/inbox";

/** Remember where a signed-out person was headed, in the browser that asked to sign in. */
export function rememberDestination(path: string): void {
  localStorage.setItem(KEY, path);
}

/** Single-slash relative paths only; anything else leads to the Inbox. Cleared on read. */
export function takeRememberedDestination(): string {
  const path = localStorage.getItem(KEY);
  localStorage.removeItem(KEY);
  if (path && path.startsWith("/") && !path.startsWith("//") && !path.includes("\\")) return path;
  return INBOX;
}
