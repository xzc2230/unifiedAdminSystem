<template>
  <div class="dev-record-page" :class="{compact:domain==='risk'}">
    <slot name="summary" :query="lastQuery" />
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <div v-if="domain==='risk'" class="list-actions">
          <a-button v-if="!readonly" v-auth="permission('add')" type="primary" @click="openEditor()">新增</a-button>
          <a-button v-if="!readonly" v-auth="permission('remove')" danger :disabled="!selected.length" @click="remove(selected)">批量删除</a-button>
        </div>
        <template v-else>
          <span>{{ title }}</span>
          <a-button v-if="!readonly" v-auth="permission('add')" type="primary" @click="openEditor()">新增</a-button>
        </template>
      </template>
      <template #toolbar>
        <template v-if="domain!=='risk'">
          <a-button v-if="!readonly" v-auth="permission('remove')" danger :disabled="!selected.length" @click="remove(selected)">批量删除</a-button>
          <slot name="toolbar" :reload="reload" />
        </template>
      </template>
      <template #tableTop>
        <a-alert v-if="domain !== 'risk' && selected.length" type="info" show-icon>
          <template #message>
            <span>已选中 {{ selected.length }} 条记录</span>
            <a-divider type="vertical" />
            <a @click="clearSelection">清空</a>
          </template>
        </a-alert>
        <span v-else></span>
      </template>
      <template #bodyCell="{column,record,text}">
        <template v-if="column.key==='action'"><a-space>
          <a v-auth="permission('query')" @click="showDetail(record)">详情</a>
          <a v-if="!readonly" v-auth="permission('edit')" @click="openEditor(record)">编辑</a>
          <a v-if="!readonly" v-auth="permission('remove')" @click="remove([record.id])">删除</a>
          <a v-if="path==='openapi/token'" v-auth="permission('query')" @click="copy(record.token)">复制 Token</a>
        </a-space></template>
        <span v-else-if="column.dataIndex==='token'">{{ maskToken(text) }}</span>
        <a-tooltip v-else-if="column.json" placement="topLeft"><template #title><pre>{{ prettyJson(text) }}</pre><a @click="copy(text)">复制</a></template><span>{{ text }}</span></a-tooltip>
        <template v-else-if="column.dataIndex==='aliveDays'">{{ displayDuration(record,'aliveDays','passDate') }}</template>
        <template v-else-if="column.dataIndex==='useDays'">{{ displayDuration(record,'useDays','customerUseDate') }}</template>
        <a v-else-if="['storeUrl','replenishUrl'].includes(column.dataIndex) && /^https?:\/\//i.test(text || '')" :href="text" target="_blank" rel="noopener noreferrer">{{ text }}</a>
        <template v-else-if="column.dataIndex==='isReplenish'">{{ Number(text)===1?'是':'否' }}</template>
        <template v-else-if="column.dataIndex==='status' && path==='openapi/token'">{{ Number(text)===1?'启用':'停用' }}</template>
      </template>
    </BasicTable>
    <a-modal v-model:open="open" :title="`${editingId==null?'新增':'编辑'}${title}`" :width="domain==='risk'?960:780" :wrapClassName="domain==='risk'?'app-editor-modal':undefined" :bodyStyle="domain==='risk'?{maxHeight:'calc(100dvh - 200px)',overflowY:'auto',padding:'20px 24px',background:'var(--app-editor-background, #f5f7fa)'}:undefined" :okText="domain==='risk'?'保存':undefined" :cancelText="domain==='risk'?'取消':undefined" :confirmLoading="saving" :maskClosable="false" @ok="save">
      <a-form ref="formRef" :model="form" layout="vertical">
        <div v-if="domain==='risk'" class="record-editor">
          <section class="editor-section">
            <header class="section-heading">
              <div><h3>规则信息</h3><p>填写匹配条件和处置动作</p></div>
              <span class="section-caption">带 * 的为必填项</span>
            </header>
            <div class="field-grid">
              <a-form-item v-for="field in fields.filter(f=>!f.readonly)" :key="field.key" :class="{ 'full-width': field.type==='textarea' }" :label="field.label" :name="field.key" :rules="field.required?[{required:true,message:`请填写${field.label}`}]:[]">
                <a-select v-if="field.options" v-model:value="form[field.key]" :options="field.options" showSearch optionFilterProp="label" allowClear />
                <a-input-number v-else-if="field.type==='number'" v-model:value="form[field.key]" style="width:100%" />
                <a-textarea v-else-if="field.type==='textarea'" v-model:value="form[field.key]" :rows="3" />
                <a-input v-else v-model:value="form[field.key]" :placeholder="field.placeholder" allowClear />
                <template v-if="field.key==='asn'"><a-button size="small" class="asn-lookup" :loading="lookupLoading" @click="previewAsn">查询 ASN 组织</a-button><p v-if="asnPreview" class="asn-preview">{{ asnPreview }}</p></template>
              </a-form-item>
            </div>
          </section>
        </div>
        <a-row v-else :gutter="20">
        <a-col v-for="field in fields.filter(f=>!f.readonly)" :key="field.key" :span="field.type==='textarea'?24:12">
          <a-form-item :label="field.label" :name="field.key" :rules="field.required?[{required:true,message:`请填写${field.label}`}]:[]">
            <a-select v-if="field.options" v-model:value="form[field.key]" :options="field.options" showSearch optionFilterProp="label" allowClear />
            <a-input-number v-else-if="field.type==='number'" v-model:value="form[field.key]" style="width:100%" />
            <a-date-picker v-else-if="field.type==='date'||field.type==='datetime'" v-model:value="form[field.key]" :showTime="field.type==='datetime'" :valueFormat="field.type==='date'?'YYYY-MM-DD':'YYYY-MM-DD HH:mm:ss'" style="width:100%" />
            <a-textarea v-else-if="field.type==='textarea'" v-model:value="form[field.key]" :rows="4" />
            <a-input v-else v-model:value="form[field.key]" :placeholder="field.placeholder" allowClear />
            <template v-if="field.key==='asn'"><a-button size="small" :loading="lookupLoading" @click="previewAsn">查询 ASN 组织</a-button><p v-if="asnPreview">{{ asnPreview }}</p></template>
          </a-form-item>
        </a-col>
      </a-row></a-form>
      <a-alert v-if="path==='openapi/token'" message="Token 留空时由服务端生成；有效期留空表示永久有效。" type="info" showIcon />
    </a-modal>
    <a-drawer v-model:open="detailOpen" :title="`${title}详情`" width="760"><a-descriptions bordered :column="1"><a-descriptions-item v-for="field in fields" :key="field.key" :label="field.label"><pre class="json-value">{{ prettyJson(detail[field.key]) }}</pre><a v-if="detail[field.key]!=null" @click="copy(detail[field.key])">复制</a></a-descriptions-item></a-descriptions></a-drawer>
  </div>
</template>
<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue';
import {message,Modal} from 'ant-design-vue';
import {copyTextToClipboard} from '/@/hooks/web/useCopyToClipboard';
import {BasicTable,useTable} from '/@/components/Table';
import {devApi,lookupAsn} from '/@/api/packagemanagement/dev';
import {displayDuration,maskToken,packstatPayload,prettyJson} from '../package-dev.mjs';
const props=withDefaults(defineProps<{title:string;path:string;domain:string;fields:any[];filters:any[];readonly?:boolean;defaults?:Record<string,any>}>(),{readonly:false,defaults:()=>({})});
const emit=defineEmits<{(e:'query',query:Record<string,any>):void;(e:'changed'):void}>();
const api=computed(()=>devApi(props.path));
const permission=(action:string)=>`package:${props.domain}:${action}`;
const compact=computed(()=>props.domain==='risk');
const querySchemas=computed(()=>compact.value?props.filters.map(item=>({...item,colProps:{style:{width:'auto',flex:'0 0 auto'}},componentProps:{...(item.componentProps||{}),style:{width:'180px'}}})):props.filters);
const queryForm=computed(()=>({schemas:querySchemas.value,labelWidth:compact.value?80:90,autoSubmitOnEnter:true,showAdvancedButton:props.filters.length>3,...(compact.value?{actionColOptions:{style:{width:'auto',flex:'0 0 auto'}},rowProps:{gutter:8}}:{})}));
const selected=ref<any[]>([]),lastQuery=ref<Record<string,any>>({});
const [registerTable,{reload,clearSelectedRowKeys,setProps}]=useTable({
 title:props.title,api:async params=>{lastQuery.value=params;emit('query',params);return api.value.list(params);},
 columns:props.fields.filter(f=>!f.hidden).map(f=>({title:f.label,key:f.key,dataIndex:f.key,width:f.width||160,ellipsis:true,json:f.json})),
 rowKey:'id',bordered:true,useSearchForm:true,showTableSetting:true,
 formConfig:queryForm.value,
 actionColumn:{title:'操作',dataIndex:'action',key:'action',width:props.path==='openapi/token'?260:180,fixed:'right'},
 clickToRowSelect:props.domain!=='risk',
 rowSelection:props.readonly?undefined:{type:'checkbox',onChange:keys=>selected.value=keys},
});
watch(queryForm,formConfig=>setProps({formConfig}),{deep:true});
const open=ref(false),saving=ref(false),editingId=ref<any>(),formRef=ref(),form=reactive<Record<string,any>>({});
const detailOpen=ref(false),detail=ref<Record<string,any>>({}),lookupLoading=ref(false),asnPreview=ref('');
async function openEditor(row?:any){const data=row?await api.value.detail(row.id):{};Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,props.defaults,data);editingId.value=row?.id;asnPreview.value='';open.value=true;}
function clearSelection(){selected.value=[];clearSelectedRowKeys();}
async function refresh(){clearSelection();await reload();emit('changed');}
async function save(){await formRef.value.validate();saving.value=true;try{let data=Object.fromEntries(props.fields.filter(f=>!f.readonly).map(f=>[f.key,form[f.key]]));if(props.path==='packstat')data=packstatPayload(data);await api.value.save(data,editingId.value);message.success('保存成功');open.value=false;await refresh();}catch(e:any){message.error(e.message||'保存失败');}finally{saving.value=false;}}
function remove(ids:any[]){Modal.confirm({title:`确认删除 ${ids.length} 条记录？`,onOk:async()=>{await api.value.remove(ids);message.success('删除成功');await refresh();}});}
async function showDetail(row:any){detail.value=props.readonly?row:await api.value.detail(row.id);detailOpen.value=true;}
async function copy(value:any){try{if(!copyTextToClipboard(typeof value==='string'?value:JSON.stringify(value,null,2)))throw new Error('copy');message.success('已复制');}catch{message.error('复制失败，请从详情手动复制');}}
async function previewAsn(){if(!form.asn)return;lookupLoading.value=true;try{const data=await lookupAsn(String(form.asn));asnPreview.value=data.asnName||data.holder||data.orgKeyword||prettyJson(data);if(data.asnName)form.asnName=data.asnName;}finally{lookupLoading.value=false;}}
defineExpose({reload:refresh});
</script>
<style scoped lang="less">
.dev-record-page{padding:16px}.json-value,pre{white-space:pre-wrap;overflow-wrap:anywhere;max-width:680px;margin:0}
.dev-record-page.compact{
  .list-actions{display:flex;justify-content:flex-start;gap:8px}
  :deep(.jeecg-basic-table-header__tableTitle){justify-content:flex-start}
  :deep(.jeecg-basic-table-form-container){padding:8px 8px 0}
  :deep(.jeecg-basic-table-form-container .ant-form){padding:8px 8px 0;margin-bottom:8px}
  :deep(.jeecg-basic-table-form-container .ant-form-item){margin-bottom:8px}
  :deep(.jeecg-basic-table-header__table-title-box + div){display:none}
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
.record-editor :deep(.ant-input-number),
.record-editor :deep(.ant-select){width:100%}
.asn-lookup{margin-top:8px}
.asn-preview{margin:6px 0 0;color:var(--editor-muted);font-size:12px}
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
