import { apiFetch } from "./client";

/** The Owner dismissed the device prompt (or none was available); not a server refusal. */
export class PasskeyCancelled extends Error {
  constructor() {
    super("passkey-cancelled");
  }
}

function toBuffer(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return bytes.buffer;
}

function toBase64Url(buffer: ArrayBuffer | null | undefined): string | undefined {
  if (!buffer) return undefined;
  let binary = "";
  new Uint8Array(buffer).forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

interface DescriptorJson {
  id: string;
  type: PublicKeyCredentialType;
  transports?: AuthenticatorTransport[];
}

function descriptors(
  list: DescriptorJson[] | undefined,
): PublicKeyCredentialDescriptor[] | undefined {
  return list?.map((d) => ({ ...d, id: toBuffer(d.id) }));
}

function cancelledOr(error: unknown): never {
  if (
    error instanceof DOMException &&
    (error.name === "NotAllowedError" || error.name === "AbortError")
  )
    throw new PasskeyCancelled();
  throw error;
}

export async function canCreatePasskey(): Promise<boolean> {
  const api = (globalThis as { PublicKeyCredential?: typeof PublicKeyCredential })
    .PublicKeyCredential;
  if (!api || !navigator.credentials) return false;
  try {
    return await api.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch {
    return false;
  }
}

interface CreationOptionsJson {
  challenge: string;
  user: { id: string; name: string; displayName: string };
  excludeCredentials?: DescriptorJson[];
  [key: string]: unknown;
}

/** Options → device check → register. Throws PasskeyCancelled or ApiFailure. */
export async function createPasskey(): Promise<void> {
  const json = await apiFetch<CreationOptionsJson>("/webauthn/register/options", {
    method: "POST",
  });
  let credential: Credential | null;
  try {
    credential = await navigator.credentials.create({
      publicKey: {
        ...json,
        challenge: toBuffer(json.challenge),
        user: { ...json.user, id: toBuffer(json.user.id) },
        excludeCredentials: descriptors(json.excludeCredentials),
      } as PublicKeyCredentialCreationOptions,
    });
  } catch (error) {
    return cancelledOr(error);
  }
  if (!credential) throw new PasskeyCancelled();
  const pk = credential as PublicKeyCredential;
  const response = pk.response as AuthenticatorAttestationResponse;
  await apiFetch("/webauthn/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      publicKey: {
        credential: {
          id: pk.id,
          rawId: toBase64Url(pk.rawId),
          type: pk.type,
          response: {
            attestationObject: toBase64Url(response.attestationObject),
            clientDataJSON: toBase64Url(response.clientDataJSON),
            transports: response.getTransports?.() ?? [],
          },
          clientExtensionResults: pk.getClientExtensionResults(),
          authenticatorAttachment: pk.authenticatorAttachment ?? undefined,
        },
        label: "",
      },
    }),
  });
}

interface RequestOptionsJson {
  challenge: string;
  allowCredentials?: DescriptorJson[];
  [key: string]: unknown;
}

/** Options → device check → login. Throws PasskeyCancelled or ApiFailure. */
export async function signInWithPasskey(): Promise<{ createdAccount: boolean }> {
  const json = await apiFetch<RequestOptionsJson>("/webauthn/authenticate/options", {
    method: "POST",
  });
  let credential: Credential | null;
  try {
    credential = await navigator.credentials.get({
      publicKey: {
        ...json,
        challenge: toBuffer(json.challenge),
        allowCredentials: descriptors(json.allowCredentials),
      } as PublicKeyCredentialRequestOptions,
    });
  } catch (error) {
    return cancelledOr(error);
  }
  if (!credential) throw new PasskeyCancelled();
  const pk = credential as PublicKeyCredential;
  const response = pk.response as AuthenticatorAssertionResponse;
  return apiFetch<{ createdAccount: boolean }>("/login/webauthn", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Telex-Time-Zone": Intl.DateTimeFormat().resolvedOptions().timeZone,
    },
    body: JSON.stringify({
      id: pk.id,
      rawId: toBase64Url(pk.rawId),
      type: pk.type,
      response: {
        authenticatorData: toBase64Url(response.authenticatorData),
        clientDataJSON: toBase64Url(response.clientDataJSON),
        signature: toBase64Url(response.signature),
        userHandle: toBase64Url(response.userHandle),
      },
      clientExtensionResults: pk.getClientExtensionResults(),
      authenticatorAttachment: pk.authenticatorAttachment ?? undefined,
    }),
  });
}
