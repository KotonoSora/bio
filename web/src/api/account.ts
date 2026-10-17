import { api } from '@resume-builder/mfe-kit';

// Kotlin DTO mirrors (TL 03 "DTOs are the source of truth for the SPA type
// mirror"): names are frozen, additive-only. The shell keeps only the surface
// its pages read; the account vertical mirrors the full feature-01 set.

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

/** Login provider button set (enabled providers only, BR-01). */
export interface ProviderView {
  id: string;
  label: string;
}

export interface ProviderListResponse {
  providers: ProviderView[];
}

export const accountApi = {
  me: () => api<MeResponse>('/api/v1/me'),
};

export const authApi = {
  providers: () => api<ProviderListResponse>('/api/auth/providers'),
};
