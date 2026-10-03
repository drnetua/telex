import { useMutation, useQuery } from "@tanstack/react-query";
import { isBackground } from "./account";
import { apiFetch } from "./client";

export type LinkingStep = "phone" | "code" | "password";
export type LinkingOrigin = "inbox" | "accounts";

export interface LinkingAttempt {
  step: LinkingStep;
  origin: LinkingOrigin;
  targetLinkedAccountId: string | null;
  codeLength: number | null;
  passwordHint: string | null;
}

export type LinkingStepResult =
  | { outcome: "next"; attempt: LinkingAttempt }
  | { outcome: "linked" | "signed-in-again"; linkedAccountId: string; origin: LinkingOrigin };

export interface StartLinkingRequest {
  origin: LinkingOrigin;
  targetLinkedAccountId?: string;
}

export const linkingAttemptKey = ["linking-attempt"] as const;

const BASE = "/api/v1/linking-attempt";

const post = <T>(url: string, body?: unknown) =>
  apiFetch<T>(url, {
    method: "POST",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

export const getMyLinkingAttempt = (background = false) =>
  apiFetch<LinkingAttempt>(BASE, { background });

/** 200 resumes the open attempt, 201 opens a new one; both answer the attempt. */
export const startMyLinkingAttempt = (request: StartLinkingRequest) =>
  post<LinkingAttempt>(BASE, request);

export const cancelMyLinkingAttempt = () => apiFetch<void>(BASE, { method: "DELETE" });

export const submitLinkingPhone = (phoneNumber: string) =>
  post<LinkingStepResult>(`${BASE}/phone`, { phoneNumber });

export const submitLinkingCode = (code: string) =>
  post<LinkingStepResult>(`${BASE}/code`, { code });

export const resendLinkingCode = () => post<LinkingAttempt>(`${BASE}/code/resend`);

export const submitLinkingPassword = (password: string) =>
  post<LinkingStepResult>(`${BASE}/password`, { password });

export function useLinkingAttempt() {
  return useQuery({
    queryKey: linkingAttemptKey,
    queryFn: () => getMyLinkingAttempt(isBackground()),
    gcTime: 0,
  });
}

export function useStartLinking() {
  return useMutation({ mutationFn: startMyLinkingAttempt });
}

export function useCancelLinking() {
  return useMutation({ mutationFn: cancelMyLinkingAttempt });
}
