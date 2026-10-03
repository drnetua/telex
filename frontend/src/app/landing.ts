import { takeRememberedDestination } from "../api/destination";

/**
 * Where a sign-in lands: a new account goes to the passkey offer, which takes the remembered destination
 * once it is done; otherwise the remembered teleX page or the Inbox.
 */
export function landAfterSignIn(createdAccount: boolean): string {
  return createdAccount ? "/welcome/passkey" : takeRememberedDestination();
}
