import AxeBuilder from "@axe-core/playwright";
import { expect, type CDPSession, type Page } from "@playwright/test";
import { codeOf, linkOf, uniqueAddress, waitForMail } from "./mailpit";
import { signOutFromShell } from "./shell";

export const SIGN_IN_SUBJECT = "Sign in to teleX";
export const NEW_SIGN_IN_SUBJECT = "New sign-in to teleX";

/**
 * WCAG 2.2 AA scan of the screen state currently shown; any violation fails. The scan is retried for a few seconds so
 * a colour measured mid-transition (a fading alert, a hovered button) is not reported as a contrast failure; a
 * violation that persists still fails.
 */
export async function expectNoA11yViolations(
  page: Page,
  state: string,
): Promise<void> {
  await expect
    .poll(
      async () => {
        const results = await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
          .analyze();
        return results.violations.map(
          (v) =>
            `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(" | ")}`,
        );
      },
      { message: `axe violations on ${state}`, timeout: 5_000 },
    )
    .toEqual([]);
}

export async function requestEmail(page: Page, address: string): Promise<void> {
  await page.goto("/sign-in");
  await page.getByLabel("Email").fill(address);
  await page.getByRole("button", { name: "Email me a sign-in link" }).click();
  await expect(
    page.getByRole("heading", { name: "Check your email" }),
  ).toBeVisible();
}

/** Signs in through the emailed link (confirm page). Returns after the confirm click. */
export async function signInByLink(
  page: Page,
  address: string,
  count = 1,
): Promise<void> {
  await requestEmail(page, address);
  const mail = await waitForMail(address, SIGN_IN_SUBJECT, count);
  await page.goto(linkOf(mail));
  await page.getByRole("button", { name: /^Continue as / }).click();
}

/** Creates a brand-new account and skips the passkey offer; leaves the page on the Inbox. */
export async function signUp(
  page: Page,
  address = uniqueAddress("owner"),
): Promise<string> {
  await signInByLink(page, address);
  await expect(page).toHaveURL(/\/welcome\/passkey$/);
  await page.getByRole("button", { name: /^(Not now|Continue)$/ }).click();
  await expect(page.getByRole("heading", { name: "Inbox" })).toBeVisible();
  return address;
}

export async function signOut(page: Page): Promise<void> {
  await signOutFromShell(page);
}

export { codeOf, linkOf };

/** CDP virtual authenticator: a resident-key platform authenticator that auto-confirms. */
export async function addVirtualAuthenticator(page: Page): Promise<CDPSession> {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("WebAuthn.enable");
  await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });
  return cdp;
}
