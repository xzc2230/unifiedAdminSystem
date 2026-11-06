import { isAxiosError } from 'axios';
import { defHttp } from '/@/utils/http/axios';

export interface RuleInput {
  id: string;
  domainId: string | null;
  path: string;
  matchType: 'EXACT' | 'PREFIX';
  priority: number;
  targetId: string;
}
export interface DraftInput {
  revision: number;
  defaultTargetId: string;
  domainIds: string[];
  rules: RuleInput[];
}
export interface RoutingDraft {
  revision: number;
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED';
  input: DraftInput;
  submittedBy: string | null;
  approvedBy: string | null;
}
export interface ReleaseView {
  id: string;
  hash: string;
  createdAt: string;
  createdBy: string;
  sourceReleaseId: string | null;
  desired: boolean;
  ack: null | { releaseId: string; hash: string; appliedAt: string };
}
export interface RoutingState {
  draft: RoutingDraft | null;
  releases: ReleaseView[];
}
export interface SimulationResult {
  ruleId: string | null;
  targetId: string;
  url: string;
}
export interface RevisionResult {
  revision: number;
}

const path = (site: string) => `/site-platform/sites/${encodeURIComponent(site)}/routing`;
const options = { errorMessageMode: 'none', successMessageMode: 'none', formatDate: false } as const;
export class RoutingConflictError extends Error {}

// Keep normal Result unwrapping/auth handling, but preserve business-code 409 before
// the shared transform replaces the response with a plain Error. No global UI feedback.
async function request<T>(url: string, method: 'GET' | 'PUT' | 'POST', data?: unknown, signal?: AbortSignal): Promise<T> {
  let code: number | undefined;
  try {
    return await defHttp.request<T>({ url, method, data, signal, success: (response) => (code = response?.code) }, options);
  } catch (error) {
    if (code === 409 || (isAxiosError(error) && (error.response?.status === 409 || error.response?.data?.code === 409))) {
      throw new RoutingConflictError('草稿版本已变化，请刷新后重新编辑。');
    }
    throw error;
  }
}

export const getRouting = (site: string, signal?: AbortSignal) => request<RoutingState>(path(site), 'GET', undefined, signal);
export const saveDraft = (site: string, input: DraftInput, signal?: AbortSignal) =>
  request<RevisionResult>(`${path(site)}/draft`, 'PUT', input, signal);
export const simulateRouting = (site: string, input: { hostname: string; path: string; input: DraftInput }, signal?: AbortSignal) =>
  request<SimulationResult>(`${path(site)}/simulate`, 'POST', input, signal);
export const submitRouting = (site: string, revision: number, signal?: AbortSignal) =>
  request<void>(`${path(site)}/submit`, 'POST', { revision }, signal);
export const approveRouting = (site: string, revision: number, signal?: AbortSignal) =>
  request<void>(`${path(site)}/approve`, 'POST', { revision }, signal);
export const publishRouting = (site: string, revision: number, signal?: AbortSignal) =>
  request<void>(`${path(site)}/publish`, 'POST', { revision }, signal);
export const rollbackRouting = (site: string, release: string, revision: number, signal?: AbortSignal) =>
  request<RevisionResult>(`${path(site)}/rollback/${encodeURIComponent(release)}`, 'POST', { revision }, signal);
