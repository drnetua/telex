import { expect } from "@playwright/test";

const MAILPIT = process.env.TELEX_MAILPIT_URL ?? "http://localhost:8025";

export interface Mail {
  id: string;
  subject: string;
  to: string;
  text: string;
}

/** A unique address per test: Mailpit keeps mail from earlier runs. */
export function uniqueAddress(tag: string): string {
  return `${tag}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`;
}

async function list(
  address: string,
): Promise<{ ID: string; Subject: string; To: { Address: string }[] }[]> {
  const res = await fetch(
    `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`,
  );
  if (!res.ok) throw new Error(`Mailpit search answered ${res.status}`);
  return ((await res.json()) as { messages: never[] }).messages ?? [];
}

/** Messages to an address with the given subject, newest first. */
export async function messagesTo(
  address: string,
  subject: string,
): Promise<Mail[]> {
  const found = (await list(address)).filter((m) => m.Subject === subject);
  return Promise.all(
    found.map(async (m) => {
      const res = await fetch(`${MAILPIT}/api/v1/message/${m.ID}`);
      const body = (await res.json()) as { Text: string };
      return { id: m.ID, subject: m.Subject, to: address, text: body.Text };
    }),
  );
}

/** Waits for the `count`-th message with `subject` to `address` and returns the newest. */
export async function waitForMail(
  address: string,
  subject: string,
  count = 1,
): Promise<Mail> {
  let mails: Mail[] = [];
  await expect
    .poll(async () => (mails = await messagesTo(address, subject)).length, {
      message: `waiting for "${subject}" mail to ${address}`,
      timeout: 20_000,
    })
    .toBeGreaterThanOrEqual(count);
  return mails[0]!;
}

export function linkOf(mail: Mail): string {
  const match = mail.text.match(/https?:\/\/\S+\/sign-in\/link#\S+/);
  if (!match) throw new Error(`no sign-in link in:\n${mail.text}`);
  return match[0];
}

export function codeOf(mail: Mail): string {
  const match = mail.text.match(/sign-in code is (\d{6})/);
  if (!match) throw new Error(`no code in:\n${mail.text}`);
  return match[1]!;
}
