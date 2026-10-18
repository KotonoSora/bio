import { api } from '@resume-builder/mfe-kit';
import type { PlansResponse } from '../lib/prices';

export const plansApi = {
  plans: () => api<PlansResponse>('/api/v1/plans'),
};

/**
 * Forward wiring only (feature 36 owns the mechanics; TL 03 contract):
 * the trial CTA calls feature 36's start endpoint. Pre-36 the surface answers
 * 401 (anonymous) or 404/routing absence - the CTA renders only when
 * `trialDays` is configured, and degradation here stays honest.
 */
export const trialApi = {
  start: () => api<void>('/api/v1/billing/trial/start', { method: 'POST' }),
};
