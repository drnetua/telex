import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isBackground } from "./account";
import { PULSE_KEY } from "../shell/pulse";
import { apiFetch } from "./client";

export type LinkedAccountState = "connected" | "reconnecting" | "session_lost";

export interface MaskedPhone {
  countryCode: string;
  lastDigits: string;
}

export interface ChatSync {
  chatsSynced: number;
  chatsTotal: number | null;
  completedAt: string | null;
}

export interface LinkedAccount {
  id: string;
  displayName: string;
  phone: MaskedPhone;
  state: LinkedAccountState;
  chatSync: ChatSync;
  linkedAt: string;
}

export interface UnlinkResult {
  signOutConfirmed: boolean;
}

export const linkedAccountsKey = ["linked-accounts"] as const;

/** `+<countryCode> ••• ••<lastDigits>`; only the country code and last two digits are real (AC-01). */
export function formatMaskedPhone(phone: MaskedPhone): string {
  return `+${phone.countryCode} ••• ••${phone.lastDigits}`;
}

export function listMyLinkedAccounts(background = false): Promise<LinkedAccount[]> {
  return apiFetch<{ items: LinkedAccount[] }>("/api/v1/linked-accounts", { background }).then(
    (r) => r.items,
  );
}

export function unlinkMyLinkedAccount(id: string): Promise<UnlinkResult> {
  return apiFetch<UnlinkResult>(`/api/v1/linked-accounts/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export function useLinkedAccounts() {
  return useQuery({
    queryKey: linkedAccountsKey,
    queryFn: () => listMyLinkedAccounts(isBackground()),
  });
}

export function useUnlinkAccount() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: unlinkMyLinkedAccount,
    onSettled: () =>
      Promise.all([
        client.invalidateQueries({ queryKey: linkedAccountsKey }),
        // The banner's pulse condition must not outlive the last Session lost account.
        client.invalidateQueries({ queryKey: PULSE_KEY }),
      ]),
  });
}
