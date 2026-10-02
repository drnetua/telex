/** The one message catalog for UI copy: English, sentence case, no emoji. */
export const messages = {
  appName: "teleX",
  home: {
    title: "Your Telegram, with agents that work for you",
    body: "teleX is being set up. Sign-in and account linking come next.",
  },
  notFound: {
    title: "Page not found",
    body: "This page doesn't exist in teleX.",
    action: "Go to Inbox",
  },
  sessionEnded: {
    title: "Session ended",
    body: "Your session has ended. Sign in again to continue.",
    action: "Sign in again",
  },
  unavailable: {
    title: "teleX is unavailable",
    body: "teleX didn't answer. Check your connection, then try again.",
    retry: "Retry",
    retrying: "Retrying",
    stillDown: "Still no answer.",
  },
  signIn: {
    title: "Sign in to teleX",
    tagline: "Your Telegram, with assistants you stay in charge of.",
    emailLabel: "Email",
    submit: "Email me a sign-in link",
    submitting: "Sending email",
    divider: "or",
    note: "No passwords. The link works once and expires in 15 minutes.",
    emailInvalid: "Enter a complete email address, like me@example.com.",
  },
  checkEmail: {
    title: "Check your email",
    sent: (address: string) =>
      `We sent a sign-in link and a 6-digit code to ${address}. Open the link, or type the code here.`,
    codeLabel: "Sign-in code",
    digitLabel: (n: number) => `Digit ${n}`,
    submit: "Sign in",
    submitting: "Signing in",
    resend: "Send a new link",
    resending: "Sending email",
    note: "The link and the code work once and expire in 15 minutes.",
    codeInvalid: "Enter the 6-digit code from the email.",
    wrongCode: (left: number) =>
      `That code is not right. ${left} ${left === 1 ? "try" : "tries"} left.`,
    resent: (address: string) => `We sent a new email to ${address}. Only the newest email works.`,
    expiredTitle: "This link has expired",
    usedTitle: "This link was already used",
    voidTitle: "This code is no longer valid",
    refusalBody: (address: string) => `Send a new link to ${address} to sign in.`,
    voidBody: (address: string) => `Request a new email to ${address} to sign in.`,
  },
} as const;
