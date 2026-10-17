// Same-origin API surface of the shell. The shared core (ApiError, CSRF
// double-submit, problem+json mapping, auth predicates) lives in
// @resume-builder/mfe-kit so the shell and every MFE speak it identically.
import { api } from '@resume-builder/mfe-kit';

export const profilesApi = {
  mine: () => api<{ id: string; username: string }[]>('/api/v1/profiles/me'),
  create: (username: string, displayName?: string) =>
    api('/api/v1/profiles', { method: 'POST', body: JSON.stringify({ username, displayName }) }),
};

export const resumesApi = {
  list: () => api<{ id: string; title: string }[]>('/api/v1/resumes'),
};
