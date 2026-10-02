import { takeRememberedDestination } from "../api/destination";

/** Where a sign-in lands: a new account goes to the passkey offer; otherwise the remembered teleX page or the Inbox. */
export function landAfterSignIn(createdAccount: boolean): string {
  const remembered = takeRememberedDestination();
  return createdAccount ? "/welcome/passkey" : remembered;
}
