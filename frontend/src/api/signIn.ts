import { apiFetch } from "./client";

export interface SignInGrant {
  grantId: string;
  email: string;
}

export function requestSignInEmail(email: string): Promise<SignInGrant> {
  return apiFetch<SignInGrant>("/api/v1/sign-in/grants", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
}

export function redeemSignInCode(
  grantId: string,
  code: string,
): Promise<{ createdAccount: boolean }> {
  return apiFetch<{ createdAccount: boolean }>(
    `/api/v1/sign-in/grants/${encodeURIComponent(grantId)}/code`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Telex-Time-Zone": Intl.DateTimeFormat().resolvedOptions().timeZone,
      },
      body: JSON.stringify({ code }),
    },
  );
}
