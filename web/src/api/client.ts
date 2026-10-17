// Same-origin only — no CORS exists. Session cookie (HttpOnly/Secure/SameSite)
// terminates at the edge; CSRF double-submit header on mutations.
const CSRF_HEADER = 'X-CSRF-Token';

function csrfToken(): string {
  const m = document.cookie.match(/(?:^|; )csrf_token=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : '';
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', ...(init.headers as Record<string, string>) };
  if ((init.method ?? 'GET') !== 'GET') headers[CSRF_HEADER] = csrfToken();
  const res = await fetch(path, { ...init, headers, credentials: 'same-origin' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error((body as { detail?: string }).detail ?? `request failed: ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const accountsApi = {
  me: () => api<{ id: string; email: string; displayName: string | null }>('/api/v1/accounts/me'),
};
export const profilesApi = {
  mine: () => api<{ id: string; username: string }[]>('/api/v1/profiles/me'),
  create: (username: string, displayName?: string) =>
    api('/api/v1/profiles', { method: 'POST', body: JSON.stringify({ username, displayName }) }),
};
export const resumesApi = {
  list: () => api<{ id: string; title: string }[]>('/api/v1/resumes'),
};
