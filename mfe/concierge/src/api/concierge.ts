import { api } from '@resume-builder/mfe-kit';

// Kotlin DTO mirrors (TL 03 "DTOs are the source of truth for the SPA type
// mirror"): names are frozen, additive-only. Fields marked ADDITIVE are the
// feature's own additions (BA 04/BA 05 surfaces) and are optional so an older
// server payload still renders.

export interface SowItem {
  kind: string;
  volume: number | null;
}

export interface SlaTierView {
  tier: string;
  /** `null` = not configured (Q-01): the board says so instead of inventing a number. */
  responseWindowMinutes: number | null;
  completionWindowMinutes: number | null;
  creditRule: string;
}

export interface HandoffItemView {
  key: string;
  at: string | null;
}

export interface HandoffView {
  startedAt: string | null;
  scheduledWork: 'transfer' | 'cancel' | null;
  items: HandoffItemView[];
  completedAt: string | null;
}

export interface ApprovalResponse {
  id: string;
  taskId: string | null;
  scope: string;
  channel: 'dashboard' | 'signed_email' | string;
  approvedAt: string;
}

export type ContractStatus = 'draft' | 'active' | 'paused' | 'exited';

export interface ContractResponse {
  id: string;
  status: ContractStatus;
  slaTier: string;
  sow: SowItem[];
  pendingTransition: 'pause' | 'exit' | null;
  activatedAt: string | null;
  exitedAt: string | null;
  // ADDITIVE
  sowVersion?: number;
  pendingBy?: 'client' | 'agency' | null;
  sla?: SlaTierView | null;
  handoff?: HandoffView | null;
  setupApproval?: ApprovalResponse | null;
}

export type TaskStatus = 'requested' | 'approved' | 'in_progress' | 'done' | 'refused' | 'cancelled';

export interface TaskResponse {
  id: string;
  kind: string;
  status: TaskStatus;
  origin: 'client' | 'scheduled' | string;
  requestedAt: string;
  approvedAt: string | null;
  completedAt: string | null;
  workRefs: string[];
  refusalReason: string | null;
  // ADDITIVE
  notes?: string | null;
  referral?: string | null;
  operatorName?: string | null;
  slaMiss?: 'response' | 'completion' | null;
  approvals?: ApprovalResponse[];
}

export const conciergeApi = {
  contract: () => api<ContractResponse | null>('/api/v1/concierge'),
  tasks: () => api<TaskResponse[]>('/api/v1/concierge/tasks'),
  requestTask: (kind: string, notes: string | null) =>
    api<TaskResponse>('/api/v1/concierge/tasks', {
      method: 'POST',
      body: JSON.stringify({ kind, notes }),
    }),
  approveTask: (id: string, scope: string) =>
    api<ApprovalResponse>(`/api/v1/concierge/tasks/${encodeURIComponent(id)}/approve`, {
      method: 'POST',
      body: JSON.stringify({ scope }),
    }),
  approveSetupPlan: (scope: string) =>
    api<ApprovalResponse>('/api/v1/concierge/approve', {
      method: 'POST',
      body: JSON.stringify({ scope }),
    }),
  pause: () => api<ContractResponse>('/api/v1/concierge/pause', { method: 'POST', body: JSON.stringify({}) }),
  exit: (decision?: { scheduledWork: string; acknowledge: string }) =>
    api<ContractResponse>('/api/v1/concierge/exit', {
      method: 'POST',
      body: JSON.stringify(decision ?? {}),
    }),
  cancelTransition: () => api<void>('/api/v1/concierge/transition', { method: 'DELETE' }),
};
