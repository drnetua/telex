import { connectivity, isShellActive } from "../shell/connectivity";

export type FailureRoute = "sign-in" | "session-ended" | "unavailable" | "connectivity";

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

/** Problem extensions of telegram-link. */
export interface ProblemExtras {
  retryAt?: string;
  /** The `Retry-After` header: seconds to wait from when the answer arrived, free of clock skew. */
  retryAfterSeconds?: number;
  passwordHint?: string | null;
  limit?: number;
  step?: "phone" | "code" | "password";
}

export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly route?: FailureRoute,
    readonly attemptsLeft?: number,
    readonly email?: string,
    readonly errors: FieldError[] = [],
    readonly extras: ProblemExtras = {},
  ) {
    super(`${status} ${code}`);
  }
}

export interface ApiOptions extends Omit<RequestInit, "signal"> {
  /** Set by the query client on refetches only (ADR-0005); never on first loads or mutations. */
  background?: boolean;
  /** No-answer timeout in ms; defaults to 10 s (the pulse uses 2 s). */
  timeoutMs?: number;
}

const TIMEOUT_MS = 10_000;

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1] ?? "") : "";
}

/** No answer, a network error (status 0) or a gateway failure: teleX is unreachable, not failing. */
export function isConnectivityStatus(status: number): boolean {
  return status === 0 || status === 502 || status === 503 || status === 504;
}

/** 503 refusals a screen shows where the Owner chose the action (AC-119); they never take over the app. */
const SCREEN_HANDLED_503 = ["telegram-linking-not-set-up", "telegram-unavailable"];

function routeFor(status: number, code: string): FailureRoute | undefined {
  // A domain 503 is an answer from teleX, not a connectivity failure, so it is checked first.
  if (status === 503 && SCREEN_HANDLED_503.includes(code)) return undefined;
  if (isConnectivityStatus(status)) {
    if (!isShellActive()) return "unavailable";
    connectivity.reportNoAnswer();
    return "connectivity";
  }
  if (status === 401 && code === "unauthenticated") return "sign-in";
  if (status === 401 && code === "session-ended") return "session-ended";
  if (status === 403 || status >= 500) return "unavailable";
  return undefined;
}

function retryAfter(response: Response): number | undefined {
  const value = response.headers.get("Retry-After");
  return value !== null && /^\d+$/.test(value.trim()) ? Number(value.trim()) : undefined;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function apiFetch<T = any>(url: string, options: ApiOptions = {}): Promise<T> {
  const { background, timeoutMs = TIMEOUT_MS, ...init } = options;
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (method !== "GET" && method !== "HEAD") headers.set("X-XSRF-TOKEN", csrfToken());
  if (background) headers.set("X-Telex-Background", "1");

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers, signal: controller.signal });
  } catch {
    throw new ApiFailure(0, "unavailable", routeFor(0, "unavailable"));
  } finally {
    clearTimeout(timer);
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.clone().json()) as T;
  }
  let code = "internal-error";
  let problem: { attemptsLeft?: number; email?: string; errors?: FieldError[] } & ProblemExtras =
    {};
  try {
    const body = (await response.json()) as {
      code?: string;
      attemptsLeft?: number;
      email?: string;
      errors?: FieldError[];
    } & ProblemExtras;
    code = body.code ?? code;
    problem = body;
  } catch {
    // non-problem body: keep the default code
  }
  throw new ApiFailure(
    response.status,
    code,
    routeFor(response.status, code),
    problem.attemptsLeft,
    problem.email,
    Array.isArray(problem.errors) ? problem.errors : [],
    {
      retryAt: problem.retryAt,
      retryAfterSeconds: retryAfter(response),
      passwordHint: problem.passwordHint,
      limit: problem.limit,
      step: problem.step,
    },
  );
}
