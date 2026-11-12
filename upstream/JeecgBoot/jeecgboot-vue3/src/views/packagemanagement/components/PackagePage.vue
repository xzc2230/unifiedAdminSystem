<template>
  <div class="package-page" :class="{compact:compactList, 'apps-list':definition.domain==='apps'}">
    <a-card v-if="definition.domain === 'statistics'" title="统计汇总" size="small" class="summary-card">
      <a-form layout="inline" @submit.prevent="loadTotal">
        <a-form-item label="包名"><a-input v-model:value="totalQuery.packageCode" allowClear /></a-form-item>
        <a-form-item label="隐藏地区"><a-input v-model:value="totalQuery.subAreas" allowClear /></a-form-item>
        <a-form-item><a-space><a-button type="primary" :loading="totalLoading" @click="loadTotal">查询汇总</a-button><a-button @click="resetTotal">重置汇总</a-button></a-space></a-form-item>
      </a-form>
      <a-alert v-if="totalError" :message="totalError" type="error" />
      <a-row :gutter="24" class="totals"><a-col :span="6"><a-statistic title="包数量" :value="totals.totalPackage ?? '—'" /></a-col><a-col :span="6"><a-statistic title="注册总数" :value="totals.totalRegister ?? '—'" /></a-col><a-col :span="6"><a-statistic title="充值次数" :value="totals.totalPayment ?? '—'" /></a-col><a-col :span="6"><a-statistic title="充值金额" :value="totals.totalMoney ?? '—'" :precision="2" /></a-col></a-row>
    </a-card>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <div v-if="compactList" class="list-actions">
          <a-button v-if="definition.add !== false" v-auth="permission('add')" type="primary" @click="openEditor()">新增</a-button>
          <a-button v-if="definition.export !== false" v-auth="permission('export')" :loading="exporting" @click="exportRows">导出</a-button>
          <a-button v-auth="permission('remove')" danger :disabled="!canRemoveSelected" @click="confirmRemove(selected)">批量删除</a-button>
          <a-button v-if="definition.domain === 'request-logs'" v-auth="permission('clean')" danger @click="confirmClean">清理日志</a-button>
        </div>
        <template v-else>
          <span>{{ definition.title }}</span>
          <a-button v-if="definition.add !== false" v-auth="permission('add')" type="primary" @click="openEditor()">新增</a-button>
          <a-button v-if="definition.export !== false" v-auth="permission('export')" :loading="exporting" @click="exportRows">导出</a-button>
        </template>
      </template>
      <template #toolbar>
        <template v-if="!compactList">
          <a-button v-auth="permission('remove')" danger :disabled="!canRemoveSelected" @click="confirmRemove(selected)">批量删除</a-button>
          <a-button v-if="definition.domain === 'request-logs'" v-auth="permission('clean')" danger @click="confirmClean">清理日志</a-button>
        </template>
      </template>
      <template #tableTop>
        <a-alert v-if="!compactList && selected.length" type="info" show-icon>
          <template #message>
            <span>已选中 {{ selected.length }} 条记录</span>
            <a-divider type="vertical" />
            <a @click="clearSelection">清空</a>
          </template>
        </a-alert>
        <span v-else></span>
      </template>
      <template #bodyCell="{column,record,text}">
        <template v-if="column.key === 'action'"><a-space class="row-actions" :size="10">
          <a v-if="definition.domain !== 'apps'" v-auth="permission('query')" @click="showDetail(record)">详情</a>
          <a v-if="definition.edit !== false && (definition.domain === 'apps' ? canEditApp : hasPermission(permission('edit')))" @click="openEditor(record)">{{ definition.domain === 'apps' ? '修改' : '编辑' }}</a>
          <a v-if="canManageRow(record)" v-auth="permission('remove')" @click="confirmRemove([record[idKey]],record)">删除</a>
          <a v-if="definition.domain === 'apps' && canManageRow(record)" v-auth="permission('transfer')" @click="openSharing(record)">转移</a>
          <a v-if="definition.domain === 'apps' && canManageRow(record)" v-auth="'package:risk:query'" @click="openAuto(record)">自动拉黑</a>
        </a-space></template>
        <a-image v-else-if="['appIcon','appAdvertising','notifyIcon','headPortraitUrl'].includes(column.dataIndex) && text" :src="fileUrl(String(text).split(',')[0])" :width="40" :height="40" />
        <a v-else-if="column.dataIndex === 'storeUrl' && record.packageCode" :href="marketUrl(record.packageCode)" target="_blank" rel="noopener noreferrer">Google Play</a>
        <a v-else-if="column.dataIndex === 'appHost' && definition.domain === 'pwa' && record.appHost && record.appSub" :href="promotionUrl(record)" target="_blank" rel="noopener noreferrer">{{ promotionUrl(record) }}</a>
        <template v-else-if="column.dataIndex === 'paymentTotal'">{{ money(text) }}</template>
        <a-tooltip v-else-if="definition.domain === 'request-logs' && column.dataIndex === 'reqParams'" placement="topLeft"><template #title><pre class="detail-value">{{ prettyJson(text) }}</pre><a @click="copyValue(text)">复制</a></template><span>{{ text }}</span></a-tooltip>
        <a-tag v-else-if="definition.domain === 'apps' && column.dataIndex === 'platform'" :color="text === 'ios' ? 'blue' : 'green'">{{ displayValue(column.dataIndex, text) || '安卓' }}</a-tag>
        <a-tag v-else-if="definition.domain === 'apps' && column.dataIndex === 'status'" :color="appStatusColor(text)">{{ displayValue(column.dataIndex, text) || '—' }}</a-tag>
        <a-tag v-else-if="definition.domain === 'apps' && ['whetherRestrict','whetherUs'].includes(column.dataIndex)" :color="Number(text) === 1 ? 'orange' : 'default'">{{ displayValue(column.dataIndex, text) || '—' }}</a-tag>
        <span v-else-if="definition.domain === 'apps' && column.dataIndex === 'appleAppId'" class="apple-id">{{ text || '—' }}</span>
        <a-tooltip v-else-if="fieldsByKey[column.dataIndex] && fieldsByKey[column.dataIndex].dict && dictRemark(column.dataIndex,text)" :title="dictRemark(column.dataIndex,text)">{{ displayValue(column.dataIndex,text) }}</a-tooltip>
        <template v-else-if="definition.domain === 'store' && column.dataIndex === 'status'">{{ record.productStatus || displayValue(column.dataIndex, text) }}</template>
        <template v-else-if="fieldsByKey[column.dataIndex]">{{ displayValue(column.dataIndex,text) }}</template>
      </template>
    </BasicTable>
    <a-modal v-model:open="editorOpen" :title="definition.domain === 'apps' ? `${editId == null ? '新增' : '修改'}应用` : `${editId == null ? '新增' : '编辑'}${definition.title}`"
      :width="definition.domain === 'apps' || definition.domain === 'store' ? 960 : definition.domain === 'pwa' ? 1000 : 850"
      :wrapClassName="definition.domain === 'apps' || definition.domain === 'store' ? 'app-editor-modal' : undefined"
      :bodyStyle="definition.domain === 'apps' || definition.domain === 'store' ? { maxHeight: 'calc(100dvh - 200px)', overflowY: 'auto', padding: '20px 24px', background: 'var(--app-editor-background, #f5f7fa)' } : undefined"
      :okText="definition.domain === 'apps' ? '保存应用' : definition.domain === 'store' ? '保存' : undefined"
      :cancelText="definition.domain === 'apps' || definition.domain === 'store' ? '取消' : undefined"
      :confirmLoading="saving" :maskClosable="false" destroyOnClose @ok="save">
      <a-alert v-if="definition.domain === 'notifications' && editId == null" type="info" message="保存后将向所选应用发送通知；发送结果以服务器实际响应为准。" showIcon class="notice" />
      <a-form ref="editorForm" :model="model" layout="vertical">
        <AppEditor v-if="definition.domain === 'apps'" :model="model" :fields="editableFields" :optionsFor="optionsFor" :editing="editId != null" @update-field="setField" />
        <div v-else-if="definition.domain === 'store'" class="record-editor">
          <section class="editor-section">
            <header class="section-heading">
              <div><h3>上架信息</h3><p>填写包名、上架状态和通知邮箱</p></div>
              <span class="section-caption">带 * 的为必填项</span>
            </header>
            <div class="field-grid">
              <a-form-item v-for="field in editableFields" :key="field.key" :class="{ 'full-width': field.type === 'textarea' }" :label="field.label" :name="field.key" :rules="field.required ? [{required:true,message:`请填写${field.label}`}] : []">
                <PackageField :field="field" :value="model[field.key]" :options="optionsFor(field)" :domain="definition.domain" :disabled="Boolean(field.readonly || field.immutable && editId != null)" @update:value="setField(field,$event)" />
              </a-form-item>
            </div>
          </section>
        </div>
        <a-row v-else :gutter="20">
          <a-col v-for="field in editableFields" :key="field.key" :xs="24" :md="field.type === 'textarea' || field.key === 'appAdvertising' ? 24 : 12">
            <a-form-item :label="field.label" :name="field.key" :rules="field.required ? [{required:true,message:`请填写${field.label}`}] : []">
              <PackageField :field="field" :value="model[field.key]" :options="optionsFor(field)" :domain="definition.domain" :disabled="Boolean(field.readonly || field.immutable && editId != null)" @update:value="setField(field,$event)" />
            </a-form-item>
          </a-col>
        </a-row>
        <PwaEditor v-if="editorOpen && definition.domain === 'pwa'" ref="pwaEditor" :key="editorVersion" :value="model" :hosts="dicts.load_host || []" :appType="definition.appType!" />
      </a-form>
    </a-modal>
    <a-drawer v-model:open="detailOpen" :title="`${definition.title}详情`" width="760">
      <a-descriptions bordered :column="1"><a-descriptions-item v-for="field in detailFields" :key="field.key" :label="field.label"><pre class="detail-value">{{ displayDetail(detail[field.key]) }}</pre></a-descriptions-item></a-descriptions>
    </a-drawer>
    <AppSharingModal v-if="definition.domain === 'apps'" ref="sharingModal" @success="refresh" />
    <a-modal v-model:open="autoOpen" :title="`自动拉黑 · ${autoPkg}`" :footer="null" width="860">
      <a-space class="notice"><a-select v-model:value="autoType" allowClear placeholder="全部类型" :options="[{value:'gaid',label:'GAID'},{value:'aid',label:'AID'}]" style="width:160px" @change="loadAuto" /><a-button :loading="autoLoading" @click="loadAuto">刷新</a-button></a-space>
      <a-table :columns="[{title:'类型',dataIndex:'type'},{title:'标识',dataIndex:'value'},{title:'原因',dataIndex:'reason'},{title:'操作',key:'action'}]" :dataSource="autoRows" :loading="autoLoading" :rowKey="row=>`${row.type}:${row.value}`" :pagination="false"><template #bodyCell="{column,record}"><a v-if="column.key==='action'" v-auth="'package:risk:remove'" @click="removeAuto(record)">解除拉黑</a></template></a-table>
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { useCachedPageRefresh } from '/@/hooks/web/useCachedPageRefresh';
import { usePermissionStore } from '/@/store/modules/permission';
import { autoBlacklist,removeAutoBlacklist } from '/@/api/packagemanagement/dev';
import { APP_STATUS_OPTIONS,EDIT_FIELDS,canAppField,appEditPayload,appFieldRequired,prettyJson } from '../package-dev.mjs';
import { message, Modal } from 'ant-design-vue';
import { copyTextToClipboard } from '/@/hooks/web/useCopyToClipboard';
import { BasicTable,useTable } from '/@/components/Table';
import { initDictOptions } from '/@/utils/dict/JDictSelectUtil';
import { useGlobSetting } from '/@/hooks/setting';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useUserStore } from '/@/store/modules/user';
import { packageApi,allPwa,statisticsTotal,cleanLogs } from '/@/api/packagemanagement';
import type { PackageDefinition,PackageField as PackageFieldSpec,PackageId,PackageOption } from '/@/types/packagemanagement';
import { APP_AREA_OPTIONS, countryLabel, countryCode } from '../package-regions.mjs';
import { fromAreas,toAreas,legacyImageUrl } from '../package-conversions.mjs';
const PackageField = defineAsyncComponent(() => import('./PackageField.vue'));
const PwaEditor = defineAsyncComponent(() => import('./PwaEditor.vue'));
const AppEditor = defineAsyncComponent(() => import('./AppEditor.vue'));
import AppSharingModal from './AppSharingModal.vue';
const props=defineProps<{definition:PackageDefinition}>();
const definition=props.definition;
const freshness=useCachedPageRefresh(async()=>{
  const rows=await reload();
  if(rows===undefined) throw new Error('列表刷新失败');
  if(definition.domain==='statistics') await loadTotal();
}, ['request-logs','event-history','event-errors','app-events','landing-counts','statistics','store'].includes(definition.domain)?5_000:30_000);
const api=packageApi(definition.domain);
const {hasPermission}=usePermission();
const permissionStore=usePermissionStore();
const appPermissions=computed(()=>permissionStore.getPermCodeList as string[]);
const canEditApp=computed(()=>hasPermission('*:*:*') || EDIT_FIELDS.some(key=>canAppField(appPermissions.value,'edit',key)));
const permission=(action:string)=>`package:${definition.domain}:${action}`;
const idKey=definition.key || 'id';
const dicts=reactive<Record<string,PackageOption[]>>({});
const parentApps=ref<Record<string,any>[]>([]);
const selected=ref<PackageId[]>([]);
const selectedRows=ref<Record<string,any>[]>([]);
const sharingModal=ref<InstanceType<typeof AppSharingModal>>();
const roleAllows=(action:string)=>hasPermission(permission(action)) || hasPermission('*:*:*');
const canManageRow=(row:Record<string,any>)=>definition.domain!=='apps' || row?.canManage===true;
const manageableSelection=computed(()=>selected.value.length>0 && selected.value.every(id=>{
  const row=selectedRows.value.find(row=>String(row[idKey])===String(id));
  return row && canManageRow(row);
}));
const canRemoveSelected=computed(()=>roleAllows('remove') && (definition.domain==='apps'?manageableSelection.value:selected.value.length>0));
function clearSelection(){selected.value=[];selectedRows.value=[];clearSelectedRowKeys();}
function openSharing(record?:Record<string,any>) {
  if(!roleAllows('transfer') || !record || !canManageRow(record)) return;
  sharingModal.value?.open([{id:record[idKey],canManage:record.canManage}]);
}
const lastFilters=ref<Record<string,any>>({});
const editorOpen=ref(false),detailOpen=ref(false),saving=ref(false),exporting=ref(false);
const editId=ref<PackageId>();
const editorVersion=ref(0);
const model=reactive<Record<string,any>>({});
const initialAppModel=ref<Record<string,any>>({}),dirtyAppFields=ref(new Set<string>());
const detail=ref<Record<string,any>>({});
const editorForm=ref(),pwaEditor=ref();
const fieldsByKey=Object.fromEntries(definition.fields.map(f=>[f.key,f]));
const editableFields=computed(()=>definition.fields.filter(f=>(!f.readonly || definition.domain === 'apps' && f.key === 'userName' && editId.value == null || definition.domain === 'notifications' && ['appName','appIcon','appHost'].includes(f.key)) && (definition.domain !== 'apps' || f.key === 'userName' || editId.value == null || canAppField(appPermissions.value,'edit',f.key))).map(f=>definition.domain==='apps'?{...f,required:appFieldRequired(f,editId.value!=null,canAppField(appPermissions.value,'query',f.key)||dirtyAppFields.value.has(f.key))}:f));
const detailFields=computed(()=>[...definition.fields.filter(f=>!['startReqTime','endReqTime'].includes(f.key)),...Object.keys(detail.value).filter(k=>!fieldsByKey[k]).map(key=>({key,label:({id:'编号',eventId:'事件编号',reviewsId:'评论编号',tags:'标签',score:'评分分布',reviewsRecords:'评论集合',appCode:'推广码',appSub:'域名后缀',appType:'应用类型',userId:'归属用户',userName:'归属用户名称',createTime:'创建时间',updateTime:'更新时间',updateDate:'更新时间'} as Record<string,string>)[key] || key}))].filter(f=>f.key!=='canManage' && (definition.domain!=='apps'||canAppField(appPermissions.value,'query',f.key))));
function optionsFor(field:PackageFieldSpec):PackageOption[] {
  if(field.dict==='app_area') {
    const rows=dicts.app_area?.length?dicts.app_area:APP_AREA_OPTIONS;
    return [...new Map(rows.map(row=>[countryCode(row.value),{...row,value:countryCode(row.value),label:APP_AREA_OPTIONS.find(option=>option.value===countryCode(row.value))?.label || row.label}])).values()];
  }
  if(field.key==='status' && definition.domain==='apps') return [...APP_STATUS_OPTIONS];
  if(field.key==='status' && definition.domain==='store') return [{value:0,label:'待上架'},{value:1,label:'已上架'},{value:2,label:'已下架'}];
  if(field.type==='parent') return parentApps.value.map(v=>({value:v.id,label:`${v.appName} (${v.appHost || v.id})`}));
  return dicts[field.dict || ''] || [];
}
function displayValue(key:string,value:any) {
  const field=fieldsByKey[key];
  if(field?.dict==='app_area') return toAreas(value).map(code=>optionsFor(field).find(option=>String(option.value)===code)?.label || countryLabel(code)).join(', ');
  if(field?.dict || definition.domain==='store' && key==='status') return String(value ?? '').split(',').map(v=>optionsFor(field).find(o=>String(o.value)===v)?.label || v).join(', ');
  if(field?.options?.length) return field.options.find(option=>String(option.value)===String(value))?.label || value;
  return value;
}
function displayDetail(value:any) { return prettyJson(value); }
function dictRemark(key:string,value:any){return optionsFor(fieldsByKey[key]).find(o=>String(o.value)===String(value))?.remark || '';}
async function copyValue(value:any){try{if(!copyTextToClipboard(typeof value==='string'?value:prettyJson(value)))throw new Error('copy');message.success('已复制');}catch{message.error('复制失败，请手动复制');}}
const backend=useGlobSetting().domainUrl;
const fileUrl=(value:string)=>legacyImageUrl(value,backend);
const marketUrl=(pkg:string)=>`https://play.google.com/store/apps/details?id=${encodeURIComponent(pkg)}`;
const promotionUrl=(row:any)=>`https://${row.appHost}${row.appSub}/?pZoneID=${encodeURIComponent(row.appCode || '')}`;
const money=(value:any)=>value == null ? '0.00' : Number(value).toFixed(2);
const todayInShanghai=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const IOS_PRODUCT_STATUSES=['待上架','准备提交','等待审核','正在审核','审核被拒','元数据被拒','无效的二进制文件','开发者已拒绝','可供销售','预购','等待开发者发布','等待 Apple 发布','正在处理','已上架','已下架','被开发者下架'];
const storeStatusOptions=[{value:0,label:'待上架'},{value:1,label:'已上架'},{value:2,label:'已下架'},...IOS_PRODUCT_STATUSES.map(label=>({value:`ios:${label}`,label:`iOS${label}`}))];
const compactList=['apps','store','request-logs'].includes(definition.domain);
const compactCol={style:{width:'auto',flex:'0 0 auto'}};
const schemas=definition.filters.filter(key=>definition.domain!=='request-logs'||!['startReqTime','endReqTime'].includes(key)).filter(key=>definition.domain!=='apps'||canAppField(appPermissions.value,'query',key)).map(key=>{
  const f=fieldsByKey[key];
  const storeStatus=key==='status'&&definition.domain==='store';
  const componentProps=f?.type==='date'||f?.type==='datetime'?{valueFormat:f.type==='date'?'YYYY-MM-DD':'YYYY-MM-DD HH:mm:ss',showTime:f.type==='datetime'}:storeStatus?{mode:'multiple',allowClear:true,maxTagCount:1,options:storeStatusOptions}:{};
  return {field:key,label:f?.label || key,component:f?.type==='date' || f?.type==='datetime'?'DatePicker':storeStatus?'Select':'Input',
    componentProps:compactList?{...componentProps,style:storeStatus?{width:'240px'}:{width:'180px'}}:componentProps,colProps:compactList?compactCol:{span:6}};
});
if(definition.domain==='request-logs'){
  const today=todayInShanghai();
  schemas.push({field:'reqDate',label:'日期',component:'RangePicker',defaultValue:[today,today],componentProps:{valueFormat:'YYYY-MM-DD',format:'YYYY-MM-DD',placeholder:['开始日期','结束日期'],style:{width:'240px'}},colProps:compactCol});
}
const appColumnWidths:Record<string,number>={appName:168,platform:88,appleAppId:132,packageCode:220,url:240,status:96,whetherRestrict:96,areas:180,whetherUs:100,mark:180,userName:112,updateDate:168};
function appStatusColor(value:any){return ({0:'default',1:'green',2:'blue',3:'gold'} as Record<string,string>)[String(value)] || 'default';}
const columns=(definition.columns || definition.fields.map(f=>f.key).filter(k=>!['startReqTime','endReqTime'].includes(k))).filter(key=>definition.domain!=='apps'||key!=='applicationCode'&&canAppField(appPermissions.value,'query',key)).map(key=>({title:fieldsByKey[key]?.label || key,dataIndex:key,key,width:definition.domain==='apps'?appColumnWidths[key] || 140:['appName','packageCode','packageName','reqTime'].includes(key)?180:150,ellipsis:true}));
const [registerTable,{reload,clearSelectedRowKeys}]=useTable({
  api:async params=>{const {pageNo,pageSize,...filters}=params;const query:Record<string,any>={...params};if(definition.domain==='store'&&Array.isArray(query.status)){const picked=query.status.filter((value:unknown)=>value!=null&&String(value)!=='');delete query.status;if(picked.length)query.statusQuery=picked.join(',');}lastFilters.value=filters;if(definition.domain==='apps'){selected.value=[];selectedRows.value=[];clearSelectedRowKeys();}return freshness.trackRequest(()=>api.list({...query,...(definition.appType!=null?{appType:definition.appType}:{})}));},
  columns,rowKey:idKey,bordered:true,showTableSetting:true,useSearchForm:true,size:definition.domain==='apps'?'small':'middle',
  formConfig:{schemas:schemas as any,labelWidth:compactList?80:100,showAdvancedButton:schemas.length>3,autoSubmitOnEnter:true,...(compactList?{actionColOptions:compactCol,rowProps:{gutter:8}}:{}),...(definition.domain==='request-logs'?{fieldMapToTime:[['reqDate',['startReqTime','endReqTime'],'YYYY-MM-DD 00:00:00']]}:{})},
  actionColumn:{title:'操作',key:'action',dataIndex:'action',width:definition.domain==='apps'?268:190,fixed:'right'},
  clickToRowSelect:!compactList,
  rowSelection:{type:'checkbox',onChange:(keys,rows)=>{selected.value=keys;selectedRows.value=rows || [];}},
});
async function loadChoices() {
  const codes=[...new Set(definition.fields.map(f=>f.dict).filter(Boolean))] as string[];
  await Promise.all(codes.map(async code=>{try {const rows=await initDictOptions(code);dicts[code]=(Array.isArray(rows)?rows:[]).map(v=>({value:['app_status','app_where'].includes(code)?Number(v.value):String(v.value),label:v.text || v.label || v.title,remark:v.remark || v.description || ''}));} catch {dicts[code]=[];}}));
}
function setField(field:PackageFieldSpec,value:any) {
  model[field.key]=value;
  if(definition.domain==='apps')dirtyAppFields.value.add(field.key);
  if(field.type==='parent') {const app=parentApps.value.find(v=>String(v.id)===String(value));for(const key of ['appName','appIcon','appHost']) model[key]=app?.[key];}
}
async function openEditor(record?:Record<string,any>) {
  // Load form components before opening so the first Save sees registered fields/refs.
  await Promise.all([import('./PackageField.vue'),...(definition.domain==='apps'?[import('./AppEditor.vue')]:[]),...(definition.domain==='pwa'?[import('./PwaEditor.vue')]:[])]);
  const data=record ? await api.detail(record[idKey]) : {};
  Object.keys(model).forEach(k=>delete model[k]);
  const defaults=definition.domain==='apps' && record?{}:{status:0,whetherRestrict:0,whetherUs:0,whetherUse:0,shunt:0,downloadCount:0,reviews:0,appEvent:'0',reviewsRecords:[],tags:'[]',score:'{}'};
  Object.assign(model,defaults,data);
  if(definition.domain==='apps' && !record) model.userName=useUserStore().getUserInfo?.username || '';
  if(!Array.isArray(model.reviewsRecords)) model.reviewsRecords=[];
  if(definition.domain==='statistics' && model.subAreas!=null) model.subAreas=countryCode(model.subAreas);
  if(definition.domain==='apps') {model.areas=toAreas(model.areas);initialAppModel.value=JSON.parse(JSON.stringify(model));dirtyAppFields.value=new Set();}
  editId.value=record?.[idKey];
  if(definition.fields.some(f=>f.type==='parent')) {
    parentApps.value=[];
    if(hasPermission('package:pwa:query')) parentApps.value=await allPwa(definition.domain==='notifications'?{appType:0}:{});
  }
  editorVersion.value++;
  editorOpen.value=true;
}
async function save() {
  await editorForm.value.validate();
  saving.value=true;
  try {
    let payload=Object.fromEntries(editableFields.value.filter(f=>!f.readonly).map(f=>[f.key,model[f.key]]));
    if(definition.domain==='apps') {payload=appEditPayload(payload,appPermissions.value,editId.value!=null,initialAppModel.value,[...dirtyAppFields.value]);if('areas' in payload)payload.areas=fromAreas(payload.areas);}
    if(definition.domain==='store') payload.storeUrl=marketUrl(model.packageCode);
    if(definition.domain==='pwa') {const pwa=pwaEditor.value.payload();payload={...payload,appType:definition.appType,appSub:pwa.appSub,tags:pwa.tags,score:pwa.score,reviewsRecords:pwa.reviewsRecords};}
    await api.save(payload,editId.value);
    message.success(definition.domain==='notifications' && editId.value==null?'通知已提交发送':'保存成功');
    editorOpen.value=false;
    await refresh();
  } catch(error:any) {if(error?.message) message.error(error.message);} finally {saving.value=false;}
}
async function refresh() {selected.value=[];selectedRows.value=[];clearSelectedRowKeys();await reload();if(definition.domain==='statistics') await loadTotal();}
async function showDetail(record:any) {detail.value=await api.detail(record[idKey]);detailOpen.value=true;}
function confirmRemove(ids:PackageId[],record?:Record<string,any>) {
  const snapshot=[...ids];
  const allowed=()=>roleAllows('remove') && snapshot.length>0 && (definition.domain!=='apps' || snapshot.every(id=>{
    const row=record && String(record[idKey])===String(id)?record:selectedRows.value.find(row=>String(row[idKey])===String(id));
    return row && canManageRow(row);
  }));
  if(!allowed()) return;
  Modal.confirm({title:`确认删除 ${snapshot.length} 条记录？`,onOk:async()=>{if(!allowed())return;await api.remove(snapshot);message.success('删除成功');await refresh();}});
}
async function exportRows() {exporting.value=true;try{await api.export({...lastFilters.value,...(definition.appType!=null?{appType:definition.appType}:{})});}finally{exporting.value=false;}}
function confirmClean() {Modal.confirm({title:'确认删除全部请求日志？',content:'将删除当前账号可见的全部请求日志，不受列表筛选条件影响。此操作无法撤销。',onOk:async()=>{await cleanLogs({});message.success('日志已清理');await refresh();}});}
const totalQuery=reactive({packageCode:'',subAreas:''});
const totals=ref<Record<string,any>>({}),totalLoading=ref(false),totalError=ref('');
async function loadTotal(){totalLoading.value=true;totalError.value='';try{totals.value=await statisticsTotal({...totalQuery});}catch{totals.value={};totalError.value='汇总加载失败，请重试';}finally{totalLoading.value=false;}}
function resetTotal(){totalQuery.packageCode='';totalQuery.subAreas='';loadTotal();}
const autoOpen=ref(false),autoLoading=ref(false),autoPkg=ref(''),autoType=ref<string>(),autoRows=ref<any[]>([]);
async function openAuto(row:any){if(!canManageRow(row) || !(hasPermission('package:risk:query') || hasPermission('*:*:*')))return;autoPkg.value=row.packageCode;autoType.value=undefined;autoRows.value=[];autoOpen.value=true;await loadAuto();}
async function loadAuto(){autoLoading.value=true;try{autoRows.value=await autoBlacklist(autoPkg.value,autoType.value);}finally{autoLoading.value=false;}}
function removeAuto(row:any){Modal.confirm({title:'解除该设备的自动拉黑？',onOk:async()=>{await removeAutoBlacklist(autoPkg.value,row.type,row.value);message.success('已解除');await loadAuto();}});}
onMounted(()=>{loadChoices();if(definition.domain==='statistics') loadTotal();});
</script>
<style scoped lang="less">
.package-page{padding:16px}.summary-card{margin-bottom:16px}.totals{margin-top:16px}.notice{margin-bottom:16px}.detail-value{white-space:pre-wrap;overflow-wrap:anywhere;margin:0;max-width:560px}
.package-page.compact{
  .list-actions{display:flex;justify-content:flex-start;gap:8px}
  :deep(.jeecg-basic-table-header__tableTitle){justify-content:flex-start}
  :deep(.jeecg-basic-table-form-container){padding:8px 8px 0}
  :deep(.jeecg-basic-table-form-container .ant-form){padding:8px 8px 0;margin-bottom:8px}
  :deep(.jeecg-basic-table-form-container .ant-form-item){margin-bottom:8px}
  :deep(.jeecg-basic-table-header__table-title-box + div){display:none}
}
.package-page.apps-list{
  .apple-id{font-variant-numeric:tabular-nums;letter-spacing:.02em}
  .row-actions{white-space:nowrap}
  :deep(.ant-tag){margin-inline-end:0;border-radius:4px;line-height:20px}
  :deep(.ant-table-tbody > tr > td){vertical-align:middle}
}
.record-editor{--editor-surface:#fff;--editor-border:#e4e9f0;--editor-text:#243247;--editor-muted:#66758a;color:var(--editor-text)}
.editor-section{padding:22px 24px 4px;border:1px solid var(--editor-border);border-radius:12px;background:var(--editor-surface)}
.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:20px}
.section-heading h3{margin:0;font-size:15px;font-weight:600;line-height:24px}
.section-heading p{margin:4px 0 0;color:var(--editor-muted);font-size:12px;line-height:20px}
.section-caption{padding-top:3px;color:var(--editor-muted);font-size:12px;white-space:nowrap}
.field-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 22px}
.field-grid>*{min-width:0}
.full-width{grid-column:1 / -1}
.record-editor :deep(.ant-form-item){margin-bottom:20px}
.record-editor :deep(.ant-form-item-label){padding-bottom:6px}
.record-editor :deep(.ant-form-item-label>label){height:auto;color:var(--editor-text);font-size:13px;font-weight:500}
.record-editor :deep(.ant-input),
.record-editor :deep(.ant-input-affix-wrapper),
.record-editor :deep(.ant-input-number),
.record-editor :deep(.ant-select-selector){border-radius:6px}
.record-editor :deep(.ant-input-number){width:100%}
@media (max-width:640px){.field-grid{grid-template-columns:minmax(0,1fr)}.editor-section{padding:16px 14px 0}}
</style>
<style lang="less">
  .app-editor-modal {
    .ant-modal { top: 40px; max-width: calc(100vw - 32px); padding-bottom: 24px; }
    .ant-modal-content { overflow: hidden; padding: 0; border-radius: 14px; }
    .ant-modal-header { margin: 0; padding: 20px 24px; border-bottom: 1px solid #e4e9f0; }
    .ant-modal-title { font-size: 17px; font-weight: 600; line-height: 24px; }
    .ant-modal-close { top: 19px; right: 20px; }
    .ant-modal-footer { margin: 0; padding: 14px 24px; border-top: 1px solid #e4e9f0; }
    .ant-modal-footer .ant-btn { min-width: 88px; height: 36px; border-radius: 6px; }
    .ant-modal-body { scrollbar-gutter: stable; }
    @media (max-width: 640px) {
      .ant-modal { top: 16px; max-width: calc(100vw - 20px); }
      .ant-modal-body { padding: 12px !important; max-height: calc(100dvh - 166px) !important; }
      .ant-modal-header { padding: 18px 20px; }
      .ant-modal-footer { padding: 12px 16px; }
    }
  }
  html[data-theme='dark'] .app-editor-modal {
    --app-editor-background: #141414;
    .ant-modal-header, .ant-modal-footer { border-color: #424242; }
  }
  html[data-theme='dark'] .record-editor {
    --editor-surface: #1f1f1f;
    --editor-border: #424242;
    --editor-text: #e5e7eb;
    --editor-muted: #a1aab8;
  }
</style>
