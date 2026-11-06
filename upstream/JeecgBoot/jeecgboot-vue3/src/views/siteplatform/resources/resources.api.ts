import { defHttp } from '/@/utils/http/axios';

export type SiteRole = 'ADMIN' | 'EDITOR' | 'VIEWER';
export type Environment = 'dev' | 'test' | 'staging' | 'prod';
export type ResourceFlag = boolean | 0 | 1;
export interface Site {
  id: string;
  name: string;
  environment: Environment;
  enabled: ResourceFlag;
  myRole: SiteRole;
  createdAt: string;
}
export interface SiteInput {
  name: string;
  environment: Environment;
  enabled: boolean;
}
export interface TargetInput {
  name: string;
  url: string;
  enabled: boolean;
}
export interface Target extends Omit<TargetInput, 'enabled'> {
  id: string;
  enabled: ResourceFlag;
}
export interface Domain {
  id: string;
  hostname: string;
  verified: ResourceFlag;
  verifiedAt: string | null;
  challengeExpires: string | null;
}
export interface DomainChallenge {
  name: string;
  value: string;
  expiresAt: string;
}
export interface MemberInput {
  userId: string;
  role: SiteRole;
}
export interface Member extends MemberInput {
  username: string;
  realname: string | null;
}
export interface AuditRecord {
  id: string;
  actorId: string;
  action: string;
  resourceId: string;
  summary: string;
  createdAt: string;
}
export interface SitePage {
  records: Site[];
  total: number;
}
export interface EditorContext {
  kind: 'site' | 'target' | 'domain' | 'member';
  site?: Site;
  record?: Target | Member;
  contextKey: number;
}

const base = '/site-platform/sites';
const sitePath = (site: string) => `${base}/${encodeURIComponent(site)}`;
const resourcePath = (site: string, kind: string, id?: string) => `${sitePath(site)}/${kind}${id ? `/${encodeURIComponent(id)}` : ''}`;
// Display feedback only in the still-active UI context, including after tenant changes.
const options = { errorMessageMode: 'none', successMessageMode: 'none' } as const;

export const flagOn = (value: ResourceFlag) => value === true || value === 1;
export const listSites = (params: { pageNo: number; pageSize: number }) => defHttp.get<SitePage>({ url: base, params }, options);
export const createSite = (data: SiteInput) => defHttp.post<string>({ url: base, data }, options);
export const updateSite = (site: string, data: SiteInput) => defHttp.put<void>({ url: sitePath(site), data }, options);
export const listTargets = (site: string) => defHttp.get<Target[]>({ url: resourcePath(site, 'targets') }, options);
export const addTarget = (site: string, data: TargetInput) => defHttp.post<string>({ url: resourcePath(site, 'targets'), data }, options);
export const updateTarget = (site: string, id: string, data: TargetInput) =>
  defHttp.put<void>({ url: resourcePath(site, 'targets', id), data }, options);
export const removeTarget = (site: string, id: string) => defHttp.delete<void>({ url: resourcePath(site, 'targets', id) }, options);
export const listDomains = (site: string) => defHttp.get<Domain[]>({ url: resourcePath(site, 'domains') }, options);
export const bindDomain = (site: string, data: { hostname: string }) => defHttp.post<string>({ url: resourcePath(site, 'domains'), data }, options);
export const getChallenge = (site: string, id: string) =>
  defHttp.get<DomainChallenge>({ url: `${resourcePath(site, 'domains', id)}/challenge` }, options);
export const renewChallenge = (site: string, id: string) => defHttp.post<void>({ url: `${resourcePath(site, 'domains', id)}/challenge` }, options);
export const verifyDomain = (site: string, id: string) => defHttp.post<void>({ url: `${resourcePath(site, 'domains', id)}/verify` }, options);
export const releaseDomain = (site: string, id: string) => defHttp.delete<void>({ url: resourcePath(site, 'domains', id) }, options);
export const listMembers = (site: string) => defHttp.get<Member[]>({ url: resourcePath(site, 'members') }, options);
export const putMember = (site: string, data: MemberInput) => defHttp.put<void>({ url: resourcePath(site, 'members'), data }, options);
export const removeMember = (site: string, userId: string) => defHttp.delete<void>({ url: resourcePath(site, 'members', userId) }, options);
export const listAudit = (site: string) => defHttp.get<AuditRecord[]>({ url: resourcePath(site, 'audit') }, options);
