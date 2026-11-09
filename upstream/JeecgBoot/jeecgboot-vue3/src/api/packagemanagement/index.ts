import { defHttp } from '/@/utils/http/axios';
import type { PackageId, PackagePage, PackageRecord } from '/@/types/packagemanagement';
const base = '/package-admin';
const identifier = (id: PackageId) => encodeURIComponent(String(id));
export const packageApi = (domain: string) => ({
  list: (params: PackageRecord) => defHttp.get<PackagePage>({url:`${base}/${domain}/list`,params}),
  detail: (id: PackageId) => defHttp.get<PackageRecord>({url:`${base}/${domain}/${identifier(id)}`}),
  save: (data: PackageRecord, id?: PackageId) => id == null
    ? defHttp.post({url:`${base}/${domain}`,data})
    : defHttp.put({url:`${base}/${domain}/${identifier(id)}`,data}),
  remove: (ids: PackageId[]) => defHttp.delete({url:`${base}/${domain}/${ids.map(identifier).join(',')}`}),
  export: async (params: PackageRecord) => {
    const blob = await defHttp.post<Blob>({url:`${base}/${domain}/export`,params,responseType:'blob'},{isTransformResponse:false,joinParamsToUrl:true});
    if (blob.type?.includes('json')) throw new Error(await blob.text());
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href=url; link.download=`${domain}_${Date.now()}.xlsx`; link.click();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
  },
});
export const transferTargets = () => defHttp.get<Array<{id:string;displayName:string}>>({url:`${base}/apps/transfer-targets`});
export const transferApps = (ids: PackageId[], targetUser: string) => defHttp.post({url:`${base}/apps/transfer`,data:{ids,targetUser}});
export const statisticsTotal = (params: PackageRecord) => defHttp.get({url:`${base}/statistics/getTotal`,params});
export const allPwa = (params: PackageRecord = {}) => defHttp.get<PackageRecord[]>({url:`${base}/pwa/all`,params});
export const cleanLogs = (params: PackageRecord) => defHttp.delete({url:`${base}/request-logs/clean`,params},{joinParamsToUrl:true});
