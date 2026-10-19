import { api } from '@resume-builder/mfe-kit';

// Kotlin DTO mirrors (TL 03 "DTOs are the source of truth for the SPA type
// mirror"): names are frozen, additive-only. `txtValue` appears only in the
// issuing response (add/reissue) — it is never re-shown (S-2).

export interface DomainResponse {
  hostname: string;
  displayHost: string;
  status: 'pending' | 'verified' | 'unverified' | 'suspended';
  txtName: string | null;
  txtValue: string | null;
  challengeExpiresAt: string | null;
  verifiedAt: string | null;
  lastRecheckedAt: string | null;
  serving: boolean;
  challengeExpired?: boolean;
  attempts?: number;
}

/** Feature 03 owner plan read (account-service): the gate the UI mirrors. */
export interface PlanResponse {
  plan: string; // "free" | "pro"
  subscription: unknown | null;
  trial: unknown | null;
}

const H = (host: string) => encodeURIComponent(host);

export const domainsApi = {
  list: () => api<DomainResponse[]>('/api/v1/domains'),
  add: (hostname: string) =>
    api<DomainResponse>('/api/v1/domains', { method: 'POST', body: JSON.stringify({ hostname }) }),
  verify: (host: string) => api<DomainResponse>(`/api/v1/domains/${H(host)}/verify`, { method: 'POST' }),
  recheck: (host: string) => api<DomainResponse>(`/api/v1/domains/${H(host)}/recheck`, { method: 'POST' }),
  reissue: (host: string) => api<DomainResponse>(`/api/v1/domains/${H(host)}/reissue`, { method: 'POST' }),
  remove: (host: string) => api<void>(`/api/v1/domains/${H(host)}`, { method: 'DELETE' }),
};

export const planApi = {
  plan: () => api<PlanResponse>('/api/v1/plan'),
};
