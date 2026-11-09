<template>
  <div class="package-page">
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
      <template #tableTitle><span>{{ definition.title }}</span></template>
      <template #toolbar>
        <a-button v-if="definition.add !== false" v-auth="permission('add')" type="primary" @click="openEditor()">新增</a-button>
        <a-button v-auth="permission('remove')" danger :disabled="!selected.length" @click="confirmRemove(selected)">批量删除</a-button>
        <a-button v-if="definition.export !== false" v-auth="permission('export')" :loading="exporting" @click="exportRows">导出</a-button>
        <a-button v-if="definition.domain === 'apps'" v-auth="permission('transfer')" :disabled="!selected.length" @click="openTransfer">转移应用</a-button>
        <a-button v-if="definition.domain === 'request-logs'" v-auth="permission('clean')" danger @click="confirmClean">清理日志</a-button>
      </template>
      <template #bodyCell="{column,record,text}">
        <template v-if="column.key === 'action'"><a-space>
          <a v-auth="permission('query')" @click="showDetail(record)">详情</a>
          <a v-if="definition.edit !== false" v-auth="permission('edit')" @click="openEditor(record)">编辑</a>
          <a v-auth="permission('remove')" @click="confirmRemove([record[idKey]])">删除</a>
        </a-space></template>
        <a-image v-else-if="['appIcon','appAdvertising','notifyIcon','headPortraitUrl'].includes(column.dataIndex) && text" :src="fileUrl(String(text).split(',')[0])" :width="40" :height="40" />
        <a v-else-if="column.dataIndex === 'storeUrl' && record.packageCode" :href="marketUrl(record.packageCode)" target="_blank" rel="noopener noreferrer">Google Play</a>
        <a v-else-if="column.dataIndex === 'appHost' && definition.domain === 'pwa' && record.appHost && record.appSub" :href="promotionUrl(record)" target="_blank" rel="noopener noreferrer">{{ promotionUrl(record) }}</a>
        <template v-else-if="column.dataIndex === 'paymentTotal'">{{ money(text) }}</template>
        <template v-else-if="fieldsByKey[column.dataIndex]">{{ displayValue(column.dataIndex,text) }}</template>
      </template>
    </BasicTable>
    <a-modal v-model:open="editorOpen" :title="definition.domain === 'apps' ? `${editId == null ? '新增' : '编辑'}应用` : `${editId == null ? '新增' : '编辑'}${definition.title}`"
      :width="definition.domain === 'apps' ? 960 : definition.domain === 'pwa' ? 1000 : 850"
      :wrapClassName="definition.domain === 'apps' ? 'app-editor-modal' : undefined"
      :bodyStyle="definition.domain === 'apps' ? { maxHeight: 'calc(100dvh - 200px)', overflowY: 'auto', padding: '20px 24px', background: 'var(--app-editor-background, #f5f7fa)' } : undefined"
      :okText="definition.domain === 'apps' ? '保存应用' : undefined"
      :cancelText="definition.domain === 'apps' ? '取消' : undefined"
      :confirmLoading="saving" :maskClosable="false" destroyOnClose @ok="save">
      <a-alert v-if="definition.domain === 'notifications' && editId == null" type="info" message="保存后将向所选应用发送通知；发送结果以服务器实际响应为准。" showIcon class="notice" />
      <a-form ref="editorForm" :model="model" layout="vertical">
        <AppEditor v-if="definition.domain === 'apps'" :model="model" :fields="editableFields" :optionsFor="optionsFor" :editing="editId != null" @update-field="setField" />
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
    <a-modal v-model:open="transferOpen" title="转移应用" :confirmLoading="transferring" @ok="doTransfer">
      <p>已选择 {{ selected.length }} 个应用，请选择当前租户内已完成身份映射的接收人。</p>
      <a-select v-model:value="targetUser" :options="targets" :loading="targetsLoading" showSearch optionFilterProp="label" placeholder="选择接收人" style="width:100%" />
      <a-empty v-if="!targetsLoading && !targets.length" description="暂无符合条件的接收人" />
    </a-modal>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { BasicTable,useTable } from '/@/components/Table';
import { initDictOptions } from '/@/utils/dict/JDictSelectUtil';
import { useGlobSetting } from '/@/hooks/setting';
import { usePermission } from '/@/hooks/web/usePermission';
import { packageApi,allPwa,transferTargets,transferApps,statisticsTotal,cleanLogs } from '/@/api/packagemanagement';
import type { PackageDefinition,PackageField as PackageFieldSpec,PackageId,PackageOption } from '/@/types/packagemanagement';
import { fromAreas,toAreas,legacyImageUrl } from '../package-conversions.mjs';
import PackageField from './PackageField.vue';
import PwaEditor from './PwaEditor.vue';
import AppEditor from './AppEditor.vue';
const props=defineProps<{definition:PackageDefinition}>();
const definition=props.definition;
const api=packageApi(definition.domain);
const {hasPermission}=usePermission();
const permission=(action:string)=>`package:${definition.domain}:${action}`;
const idKey=definition.key || 'id';
const dicts=reactive<Record<string,PackageOption[]>>({});
const parentApps=ref<Record<string,any>[]>([]);
const selected=ref<PackageId[]>([]);
const lastFilters=ref<Record<string,any>>({});
const editorOpen=ref(false),detailOpen=ref(false),saving=ref(false),exporting=ref(false);
const editId=ref<PackageId>();
const editorVersion=ref(0);
const model=reactive<Record<string,any>>({});
const detail=ref<Record<string,any>>({});
const editorForm=ref(),pwaEditor=ref();
const fieldsByKey=Object.fromEntries(definition.fields.map(f=>[f.key,f]));
const editableFields=computed(()=>definition.fields.filter(f=>!f.readonly || definition.domain === 'notifications' && ['appName','appIcon','appHost'].includes(f.key)));
const detailFields=computed(()=>[...definition.fields.filter(f=>!['startReqTime','endReqTime'].includes(f.key)),...Object.keys(detail.value).filter(k=>!fieldsByKey[k]).map(key=>({key,label:({id:'编号',eventId:'事件编号',reviewsId:'评论编号',tags:'标签',score:'评分分布',reviewsRecords:'评论集合',appCode:'推广码',appSub:'域名后缀',appType:'应用类型',userId:'归属用户',userName:'归属用户名称',createTime:'创建时间',updateTime:'更新时间'} as Record<string,string>)[key] || key}))]);
function optionsFor(field:PackageFieldSpec):PackageOption[] {
  if(field.key==='status' && definition.domain==='store') return [{value:0,label:'待上架'},{value:1,label:'已上架'},{value:2,label:'已下架'}];
  if(field.type==='parent') return parentApps.value.map(v=>({value:v.id,label:`${v.appName} (${v.appHost || v.id})`}));
  return dicts[field.dict || ''] || [];
}
function displayValue(key:string,value:any) {
  const field=fieldsByKey[key];
  if(field?.dict || definition.domain==='store' && key==='status') return String(value ?? '').split(',').map(v=>optionsFor(field).find(o=>String(o.value)===v)?.label || v).join(', ');
  return value;
}
function displayDetail(value:any) { return typeof value==='object' ? JSON.stringify(value,null,2) : value ?? '—'; }
const backend=useGlobSetting().domainUrl;
const fileUrl=(value:string)=>legacyImageUrl(value,backend);
const marketUrl=(pkg:string)=>`https://play.google.com/store/apps/details?id=${encodeURIComponent(pkg)}`;
const promotionUrl=(row:any)=>`https://${row.appHost}${row.appSub}/?pZoneID=${encodeURIComponent(row.appCode || '')}`;
const money=(value:any)=>value == null ? '0.00' : Number(value).toFixed(2);
const schemas=definition.filters.map(key=>{
  const f=fieldsByKey[key];
  return {field:key,label:f?.label || key,component:f?.type==='date' || f?.type==='datetime'?'DatePicker':key==='status'&&definition.domain==='store'?'Select':'Input',
    componentProps:f?.type==='date'||f?.type==='datetime'?{valueFormat:f.type==='date'?'YYYY-MM-DD':'YYYY-MM-DD HH:mm:ss',showTime:f.type==='datetime'}:key==='status'&&definition.domain==='store'?{options:optionsFor(f)}:{},colProps:{span:6}};
});
const columns=(definition.columns || definition.fields.map(f=>f.key).filter(k=>!['startReqTime','endReqTime'].includes(k))).map(key=>({title:fieldsByKey[key]?.label || key,dataIndex:key,key,width:['appName','packageCode','packageName','reqTime'].includes(key)?180:150,ellipsis:true}));
const [registerTable,{reload,clearSelectedRowKeys}]=useTable({
  api:async params=>{const {pageNo,pageSize,...filters}=params;lastFilters.value=filters;return api.list({...params,...(definition.appType!=null?{appType:definition.appType}:{})});},
  columns,rowKey:idKey,bordered:true,showTableSetting:true,useSearchForm:true,
  formConfig:{schemas:schemas as any,labelWidth:100,showAdvancedButton:schemas.length>3,autoSubmitOnEnter:true},
  actionColumn:{title:'操作',key:'action',dataIndex:'action',width:190,fixed:'right'},
  rowSelection:{type:'checkbox',onChange:keys=>selected.value=keys},
});
async function loadChoices() {
  const codes=[...new Set(definition.fields.map(f=>f.dict).filter(Boolean))] as string[];
  await Promise.all(codes.map(async code=>{try {const rows=await initDictOptions(code);dicts[code]=(Array.isArray(rows)?rows:[]).map(v=>({value:['app_status','app_where'].includes(code)?Number(v.value):String(v.value),label:v.text || v.label || v.title}));} catch {dicts[code]=[];}}));
}
function setField(field:PackageFieldSpec,value:any) {
  model[field.key]=value;
  if(field.type==='parent') {const app=parentApps.value.find(v=>String(v.id)===String(value));for(const key of ['appName','appIcon','appHost']) model[key]=app?.[key];}
}
async function openEditor(record?:Record<string,any>) {
  const data=record ? await api.detail(record[idKey]) : {};
  Object.keys(model).forEach(k=>delete model[k]);
  Object.assign(model,{status:0,whetherRestrict:0,whetherUs:0,whetherUse:0,shunt:0,downloadCount:0,reviews:0,appEvent:'0',reviewsRecords:[],tags:'[]',score:'{}'},data);
  if(!Array.isArray(model.reviewsRecords)) model.reviewsRecords=[];
  if(definition.domain==='apps') model.areas=toAreas(model.areas);
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
    let payload=Object.fromEntries(definition.fields.filter(f=>!f.readonly).map(f=>[f.key,model[f.key]]));
    if(definition.domain==='apps') payload.areas=fromAreas(model.areas);
    if(definition.domain==='store') payload.storeUrl=marketUrl(model.packageCode);
    if(definition.domain==='pwa') {const pwa=pwaEditor.value.payload();payload={...payload,appType:definition.appType,appSub:pwa.appSub,tags:pwa.tags,score:pwa.score,reviewsRecords:pwa.reviewsRecords};}
    await api.save(payload,editId.value);
    message.success(definition.domain==='notifications' && editId.value==null?'通知已提交发送':'保存成功');
    editorOpen.value=false;
    await refresh();
  } catch(error:any) {if(error?.message) message.error(error.message);} finally {saving.value=false;}
}
async function refresh() {selected.value=[];clearSelectedRowKeys();await reload();if(definition.domain==='statistics') await loadTotal();}
async function showDetail(record:any) {detail.value=await api.detail(record[idKey]);detailOpen.value=true;}
function confirmRemove(ids:PackageId[]) {Modal.confirm({title:`确认删除 ${ids.length} 条记录？`,onOk:async()=>{await api.remove(ids);message.success('删除成功');await refresh();}});}
async function exportRows() {exporting.value=true;try{await api.export({...lastFilters.value,...(definition.appType!=null?{appType:definition.appType}:{})});}finally{exporting.value=false;}}
function confirmClean() {Modal.confirm({title:'清理符合当前筛选条件的日志？',content:'仅清理当前有权访问的应用日志。此操作无法撤销。',onOk:async()=>{await cleanLogs(lastFilters.value);message.success('日志已清理');await refresh();}});}
const totalQuery=reactive({packageCode:'',subAreas:''});
const totals=ref<Record<string,any>>({}),totalLoading=ref(false),totalError=ref('');
async function loadTotal(){totalLoading.value=true;totalError.value='';try{totals.value=await statisticsTotal({...totalQuery});}catch{totals.value={};totalError.value='汇总加载失败，请重试';}finally{totalLoading.value=false;}}
function resetTotal(){totalQuery.packageCode='';totalQuery.subAreas='';loadTotal();}
const transferOpen=ref(false),targetsLoading=ref(false),transferring=ref(false),targetUser=ref<string>();
const targets=ref<PackageOption[]>([]);
async function openTransfer(){targetUser.value=undefined;targets.value=[];transferOpen.value=true;targetsLoading.value=true;try{targets.value=(await transferTargets()).map(v=>({value:v.id,label:v.displayName}));}finally{targetsLoading.value=false;}}
async function doTransfer(){if(!targetUser.value){message.warning('请选择接收人');return;}transferring.value=true;try{await transferApps(selected.value,targetUser.value);message.success('应用已转移');transferOpen.value=false;await refresh();}finally{transferring.value=false;}}
onMounted(()=>{loadChoices();if(definition.domain==='statistics') loadTotal();});
</script>
<style scoped lang="less">.package-page{padding:16px}.summary-card{margin-bottom:16px}.totals{margin-top:16px}.notice{margin-bottom:16px}.detail-value{white-space:pre-wrap;overflow-wrap:anywhere;margin:0;max-width:560px}</style>
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
</style>
