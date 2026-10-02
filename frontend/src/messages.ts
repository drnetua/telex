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
} as const;
