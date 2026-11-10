export const EDIT_FIELDS = Object.freeze(['appName','author','packageCode','url','status','whetherRestrict','areas','mark','whetherUs','gaidAidThreshold','aidDeviceThreshold','aidGaidSwitchThreshold','riskWindowSeconds','referrerBlacklistJson']);
export const QUERY_FIELDS = Object.freeze(['appName','author','userName','packageCode','status','url','whetherRestrict','areas','whetherUs','mark','updateDate']);
export function canAppField(permissions, action, key) {
 const granted=Array.isArray(permissions)?permissions:[];
 const fields=action==='edit'?EDIT_FIELDS:QUERY_FIELDS;
 const field=key==='updateTime'?'updateDate':key;
 if(granted.includes('*:*:*')) return true;
 if(!fields.includes(field)) return granted.includes(`package:apps:${action}`);
 const prefix=`package:apps:${action}Field:`;
 return granted.includes(prefix+field) || granted.includes(`package:apps:${action}`) && !fields.some(k=>granted.includes(prefix+k));
}
export function appEditPayload(model, permissions, editing, initial = {}, touched = []) {
 return Object.fromEntries(Object.entries(model).filter(([key,value])=>{
  if(!editing) return true;
  if(key==='packageCode' || !canAppField(permissions,'edit',key) || value===undefined) return false;
  const changed=JSON.stringify(value)!==JSON.stringify(initial[key]);
  const explicitlyEditedRedacted=touched.includes(key) && !canAppField(permissions,'query',key);
  return changed || explicitlyEditedRedacted;
 }));
}
export function appFieldRequired(field, editing, queried = true) {
 return Boolean(field.required && (!editing || !field.immutable && queried));
}
export function prettyJson(value) {
 if(value==null || value==='') return '—';
 try{return JSON.stringify(typeof value==='string'?JSON.parse(value):value,null,2);}catch{return String(value);}
}
export function maskToken(token) {const s=String(token||'');return s.length<=8?'*'.repeat(s.length):s.slice(0,4)+'*'.repeat(Math.min(s.length-8,16))+s.slice(-4);}
export const SURVIVE_DAY_KEYS=Object.freeze([7,8,9,10,11,12,13,14,15]);
export function summaryRows(data={}) {
 if(data.noRecord===true || data.noRecord===1 || data.noRecord==='true') return [];
 return [['s','存活时间'],['u','使用时间']].map(([prefix,metric])=>Object.assign({metric},Object.fromEntries(SURVIVE_DAY_KEYS.map(d=>['d'+d,data[prefix+d]??(prefix==='s'?data[d]:0)??0]))));
}
export function displayDuration(row,key,start) {
 const value=row[key]??(key==='aliveDays'?row.surviveDays:undefined);
 if(value!=null && value!=='') return value;
 if(!row.dropDate || !row[start]) return '';
 const date=v=>Date.parse(String(v).slice(0,10)+'T00:00:00Z');
 const diff=Math.round((date(row.dropDate)-date(row[start]))/86400000);
 return Number.isFinite(diff)?Math.max(0,diff):'';
}
export function packstatPayload(model) {
 if(Number(model.isReplenish)===1 && !String(model.replenishUrl||'').trim()) throw new Error('补包链接不能为空');
 const data=Object.fromEntries(Object.entries(model).map(([k,v])=>[k,v===''||v===undefined?null:v]));
 data.isReplenish=Number(data.isReplenish)===1?1:0;
 if(data.isReplenish===0)data.replenishUrl=null;
 return data;
}
export const OPEN_API_ACTIONS=Object.freeze([
 {key:'list',title:'应用列表',method:'GET',path:'/openapi/manager/list'},
 {key:'detail',title:'应用详情',method:'GET',path:'/openapi/manager/{id}'},
 {key:'add',title:'新增应用',method:'POST',path:'/openapi/manager'},
 {key:'edit',title:'修改应用',method:'PUT',path:'/openapi/manager'},
 {key:'remove',title:'删除应用',method:'DELETE',path:'/openapi/manager/{ids}'},
]);
export function openApiRequest(action,form) {
 const api=OPEN_API_ACTIONS.find(a=>a.key===action);
 if(!api)throw new Error('不支持的接口');
 if(!String(form.token||'').trim())throw new Error('请填写 Token');
 let path=api.path;
 if(path.includes('{id')){
  if(!String(form.id||'').trim())throw new Error('请填写记录 ID');
  path=path.replace(/\{ids?\}/,String(form.id).split(',').map(v=>encodeURIComponent(v.trim())).join(','));
 }
 if(action==='list' && form.query)path+='?'+new URLSearchParams(form.query).toString();
 const request={method:api.method,path,headers:{'X-Api-Token':form.token.trim(),'Content-Type':'application/json'}};
 if(['POST','PUT'].includes(api.method)){
  try{request.body=JSON.stringify(JSON.parse(form.body||'{}'));}catch{throw new Error('请求正文必须是合法 JSON');}
 }
 return request;
}
