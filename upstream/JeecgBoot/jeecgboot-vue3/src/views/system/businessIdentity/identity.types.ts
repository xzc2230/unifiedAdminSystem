export type IdentityState = 'UNOPENED' | 'ACTIVE' | 'DISABLED' | 'ANOMALOUS';

export interface TenantOption {
  tenantId: number;
  tenantName: string;
  status: number | string;
}

export interface IdentityRow {
  userId: string;
  username: string;
  realname: string;
  tenantId: number;
  tenantName: string;
  userStatus: number | string;
  memberStatus: number | string;
  tenantActive: boolean;
  legacyUserId: string | null;
  identityState: IdentityState;
  source: 'NEW' | 'LEGACY' | 'MIGRATED' | null;
}

export interface Target {
  tenantId: number;
  userId: string;
}

export interface LegacyTarget extends Target {
  legacyUserId: string;
}

export interface LegacyPreview {
  legacyUserId: string;
  available: boolean;
  conflict: string | null;
  resourceCounts: { resourceType: string; count: number }[];
  ownershipConflicts: { resourceType: string; resourceId: string; tenantId: number; ownerUserId: string }[] | number;
}

export interface AuditRow {
  id: string;
  operatorId: string;
  operatorName: string;
  action: string;
  legacyUserId: string;
  beforeState: string | null;
  afterState: string | null;
  reason: string;
  createdAt: string;
}

export interface Page<T> {
  records: T[];
  total: number;
}
