import type { AxiosRequestConfig } from 'axios';
import { defHttp } from '/@/utils/http/axios';
import type { ApplicationRow, AttributionOffer, Capabilities, Configuration, SavePayload } from './attribution.types';

const base = '/attribution-admin';
const channelPath = (row: ApplicationRow) => `${encodeURIComponent(row.applicationId)}/channels/${encodeURIComponent(row.channelId)}`;

// Keep the host's auth, tenant and signing interceptors, but retain structured business errors.
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const result = await defHttp.request<{ code: number; success?: boolean; message?: string; result: T }>(config, {
    isTransformResponse: false,
    errorMessageMode: 'none',
    successMessageMode: 'none',
  });
  if (![0, 200].includes(Number(result.code)) || result.success === false) throw result;
  return result.result;
}

export const listApplications = (params: { q: string; pageNo: number; pageSize: number }) =>
  request<{ records: ApplicationRow[]; total: number }>({ url: `${base}/applications`, method: 'GET', params });

export const getConfiguration = (row: ApplicationRow) =>
  request<Configuration>({ url: `${base}/application-configurations/${channelPath(row)}`, method: 'GET' });

export const getOffers = (row: ApplicationRow) =>
  request<{ configVersion: string; offers: AttributionOffer[] }>({
    url: `${base}/applications/${channelPath(row)}/offers`,
    method: 'GET',
    params: { configVersion: row.configVersion },
  });

export const getCapabilities = () => request<Capabilities>({ url: `${base}/capabilities`, method: 'GET' });

export const saveConfiguration = (row: ApplicationRow | undefined, payload: SavePayload, key: string) =>
  request<{ applicationId: string; channelId: string; configVersion: string }>({
    url: `${base}/application-configurations${row ? `/${channelPath(row)}` : ''}`,
    method: row ? 'PUT' : 'POST',
    data: payload,
    headers: { 'Idempotency-Key': key },
  });
