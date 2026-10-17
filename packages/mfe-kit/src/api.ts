// Same-origin only — no CORS exists. The session cookie (HttpOnly/Secure/SameSite)
// terminates at the edge; CSRF double-submit header on mutations. No token is ever
// JS-readable beyond the CSRF cookie itself. Shared by the shell and every MFE
// bundle (build-time source, no shared runtime instance).
const CSRF_HEADER = 'X-CSRF-Token';

/** RFC 9457 problem body surface (feature 01): stable `code`, optional Retry-After. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly retryAfterSeconds: number | null;

  constructor(status: number, code: string, detail: string, retryAfterSeconds: number | null = null) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function csrfToken(): string {
  const m = document.cookie.match(/(?:^|; )csrf_token=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : '';
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...(init.headers as Record<string, string>) };
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';
  if ((init.method ?? 'GET') !== 'GET') headers[CSRF_HEADER] = csrfToken();
  const res = await fetch(path, { ...init, headers, credentials: 'same-origin' });
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

/** RFC 9457 body -> ApiError; used by `api` and by manual-redirect probes. */
export async function toApiError(res: Response): Promise<ApiError> {
  const body = (await res.json().catch(() => ({}))) as { code?: string; detail?: string };
  return new ApiError(
    res.status,
    body.code ?? 'request_failed',
    body.detail ?? `Request failed (${res.status}).`,
    retryAfterSeconds(res),
  );
}

function retryAfterSeconds(res: Response): number | null {
  const raw = res.headers.get('Retry-After');
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

/** True when the error means the edge session is gone and the surface must log in again. */
export function isUnauthenticated(err: unknown): boolean {
  return err instanceof ApiError && err.status === 401;
}

/** Apple private relay / Facebook relay addresses: deliverable only via the contact email (BR-03). */
export function isRelayEmail(email: string): boolean {
  const domain = email.split('@')[1]?.toLowerCase() ?? '';
  return domain === 'privaterelay.appleid.com' || domain === 'facebook.com';
}
