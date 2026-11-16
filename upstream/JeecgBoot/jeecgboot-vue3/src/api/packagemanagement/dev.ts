import { defHttp } from '/@/utils/http/axios';
import type { PackageId, PackageRecord, PackagePage } from '/@/types/packagemanagement';
const base='/package-admin';
const id=(value:PackageId)=>encodeURIComponent(String(value));
export const devApi=(path:string)=>({
 list:(params:PackageRecord)=>defHttp.get<PackagePage>({url:`${base}/${path}/list`,params}),
 detail:(key:PackageId)=>defHttp.get<PackageRecord>({url:`${base}/${path}/${id(key)}`}),
 save:(data:PackageRecord,key?:PackageId)=>key==null?defHttp.post({url:`${base}/${path}`,data}):defHttp.put({url:`${base}/${path}`,data:{...data,id:key}}),
 remove:(keys:PackageId[])=>defHttp.delete({url:`${base}/${path}/${keys.map(id).join(',')}`}),
});
export const packstatAuthors=()=>defHttp.get<any[]>({url:`${base}/packstat/authors`});
export const packstatRegions=()=>defHttp.get<any[]>({url:`${base}/packstat/regions`});
export const packstatSummary=(params:PackageRecord)=>defHttp.get<PackageRecord>({url:`${base}/packstat/surviveSummary`,params});
export const exportPackstat=async(params:PackageRecord)=>{
 const blob=await defHttp.post<Blob>({url:`${base}/packstat/export`,params,responseType:'blob'},{isTransformResponse:false,joinParamsToUrl:true});
 if(blob.type?.includes('json'))throw new Error(await blob.text());
 const packer=String(params.packer||'').trim();
 const name=packer?`包统计绩效-${params.customerCategory}-${packer}-${params.useMonth}.xlsx`:`包统计绩效-${params.customerCategory}-${params.useMonth}.xlsx`;
 const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
};
export const lookupAsn=(asn:string)=>defHttp.get<PackageRecord>({url:`${base}/risk/asn/lookup/${encodeURIComponent(asn)}`});
export const autoBlacklist=(pkg:string,type?:string)=>defHttp.get<any[]>({url:`${base}/risk/auto/list`,params:{pkg,type}});
export const removeAutoBlacklist=(pkg:string,type:string,value:string)=>defHttp.delete({url:`${base}/risk/auto`,params:{pkg,type,value}},{joinParamsToUrl:true});
