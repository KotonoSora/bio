import { api } from '@resume-builder/mfe-kit';

// Kotlin DTO mirrors (TL 03 "DTOs are the source of truth for the SPA type
// mirror"): names are frozen, additive-only.

export interface MeResponse {
  id: string;
  name: string | null;
  email: string | null;
  emailVerified: boolean;
  emailConflict: boolean;
  contactEmail: string | null;
  contactVerified: boolean;
  imageKey: string | null;
  createdAt: string;
}

export interface SessionSummary {
  sid: string;
  current: boolean;
  deviceLabel: string | null;
  ipClass: string | null;
  createdAt: string;
  lastActiveAt: string;
}

export interface SessionListResponse {
  sessions: SessionSummary[];
}

/** Login/step-up provider button set (enabled providers only, BR-01). */
export interface ProviderView {
  id: string;
  label: string;
}

export interface ProviderListResponse {
  providers: ProviderView[];
}

/** Settings provider row: enabled ∪ linked, with owner link state. */
export interface MyProviderView extends ProviderView {
  linked: boolean;
}

export interface MyProviderListResponse {
  providers: MyProviderView[];
}

/** profile-service read (feature 01 handoff shape): feeds the typed confirmation (BR-01). */
export interface ProfileLite {
  id: string;
  username: string;
  displayName: string | null;
  isPublic: boolean;
}

export type DeletionStatusValue = 'none' | 'grace' | 'cancelled';

/** Feature 02 owner view (TL 03): `none` until a request exists; `purged` is not a client state (410). */
export interface DeleteStatusResponse {
  status: DeletionStatusValue;
  requestedAt: string | null;
  purgeDueAt: string | null;
  certificateRef: string | null;
}

export const profileApi = {
  mine: () => api<ProfileLite[]>('/api/v1/profiles/me'),
};

export const accountApi = {
  me: () => api<MeResponse>('/api/v1/me'),
  sessions: () => api<SessionListResponse>('/api/v1/me/sessions'),
  revokeSession: (sid: string) =>
    api<void>(`/api/v1/me/sessions/${encodeURIComponent(sid)}`, { method: 'DELETE' }),
  revokeOtherSessions: () => api<void>('/api/v1/me/sessions', { method: 'DELETE' }),
  providers: () => api<MyProviderListResponse>('/api/v1/me/providers'),
  sendContactEmail: (email: string) =>
    api<void>('/api/v1/me/contact-email', { method: 'POST', body: JSON.stringify({ email }) }),
  verifyContactEmail: (code: string) =>
    api<MeResponse>('/api/v1/me/contact-email/verify', { method: 'POST', body: JSON.stringify({ code }) }),
  signOut: () => api<void>('/api/auth/sign-out', { method: 'POST' }),
};

export const authApi = {
  providers: () => api<ProviderListResponse>('/api/auth/providers'),
  unlink: (provider: string) =>
    api<void>(`/api/auth/unlink/${encodeURIComponent(provider)}`, { method: 'POST' }),
};

/**
 * Feature 02 owner surface (TL 03): request needs a fresh step-up + the exact
 * typed confirmation; cancel (restore) is login + one click. On request commit
 * every session is revoked server-side, the caller's included - the 200
 * response is the panel data, not a polling source.
 */
export const deletionApi = {
  status: () => api<DeleteStatusResponse>('/api/v1/account/delete-status'),
  request: (confirmation: string) =>
    api<DeleteStatusResponse>('/api/v1/account/delete-request', {
      method: 'POST',
      body: JSON.stringify({ confirmation }),
    }),
  cancel: () => api<DeleteStatusResponse>('/api/v1/account/delete-cancel', { method: 'POST' }),
};
