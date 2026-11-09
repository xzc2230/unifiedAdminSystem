import { defHttp } from '/@/utils/http/axios';
import type { AuditRow, IdentityRow, IdentityState, LegacyPreview, LegacyTarget, Page, Target, TenantOption } from './identity.types';

const base = '/package-admin/identities';
const options = { errorMessageMode: 'none', successMessageMode: 'none' } as const;

export const getTenants = () => defHttp.get<TenantOption[]>({ url: `${base}/tenants` }, options);
export const getIdentities = (params: { tenantId: number; keyword?: string; state?: IdentityState; pageNo: number; pageSize: number }) =>
  defHttp.get<Page<IdentityRow>>({ url: `${base}/list`, params }, options);
export const getLegacyPreview = (params: LegacyTarget) => defHttp.get<LegacyPreview>({ url: `${base}/legacy-preview`, params }, options);
export const openIdentity = (data: Target & { reason: string }) => defHttp.post<IdentityRow>({ url: `${base}/open`, data }, options);
export const bindIdentity = (data: LegacyTarget & { reason: string }) => defHttp.post<IdentityRow>({ url: `${base}/bind`, data }, options);
export const changeIdentityStatus = (data: Target & { enabled: boolean; reason: string }) =>
  defHttp.post<IdentityRow>({ url: `${base}/status`, data }, options);
export const getIdentityAudit = (params: Target & { pageNo: number; pageSize: number }) =>
  defHttp.get<Page<AuditRow>>({ url: `${base}/audit`, params }, options);
