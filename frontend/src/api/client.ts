export type FailureRoute = "sign-in" | "session-ended" | "unavailable";

export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly route?: FailureRoute,
    readonly attemptsLeft?: number,
    readonly email?: string,
  ) {
    super(`${status} ${code}`);
  }
}

export interface ApiOptions extends Omit<RequestInit, "signal"> {
  /** Set by the query client on refetches only (ADR-0005); never on first loads or mutations. */
  background?: boolean;
}

const TIMEOUT_MS = 10_000;

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1] ?? "") : "";
}

function routeFor(status: number, code: string): FailureRoute | undefined {
  if (status === 401 && code === "unauthenticated") return "sign-in";
  if (status === 401 && code === "session-ended") return "session-ended";
  if (status === 403 || status >= 500) return "unavailable";
  return undefined;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function apiFetch<T = any>(url: string, options: ApiOptions = {}): Promise<T> {
  const { background, ...init } = options;
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (method !== "GET" && method !== "HEAD") headers.set("X-XSRF-TOKEN", csrfToken());
  if (background) headers.set("X-Telex-Background", "1");

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers, signal: controller.signal });
  } catch {
    throw new ApiFailure(0, "unavailable", "unavailable");
  } finally {
    clearTimeout(timer);
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.clone().json()) as T;
  }
  let code = "internal-error";
  let problem: { attemptsLeft?: number; email?: string } = {};
  try {
    const body = (await response.json()) as {
      code?: string;
      attemptsLeft?: number;
      email?: string;
    };
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
  );
}
