<template><div class="openapi-docs"><a-card title="OpenAPI 应用管理接口">
 <a-alert message="使用启用且未过期的 X-Api-Token；只能读写当前 Token 所属应用。" type="info" showIcon />
 <a-table :columns="columns" :dataSource="OPEN_API_ACTIONS" rowKey="key" :pagination="false" size="small" />
 <a-descriptions bordered :column="1" size="small"><a-descriptions-item label="请求格式">POST / PUT 使用 application/json。列表查询使用 pageNum、pageSize，可按 packageCode、appName、status 筛选。</a-descriptions-item><a-descriptions-item label="应用字段">appName（应用名）、author（出包人）、packageCode（包名）、url（链接）、status（0关/1全开/2归因/3调试）、whetherRestrict（地区限制）、areas（地区逗号串）、mark（备注）、whetherUs（禁美）、blockedProviders（禁用运营商，逗号分隔 google,apple,meta,facebook,tiktok）、whetherUse（自用）、gaidAidThreshold、aidDeviceThreshold、aidGaidSwitchThreshold、riskWindowSeconds、referrerBlacklistJson。</a-descriptions-item><a-descriptions-item label="响应">沿用旧接口 AjaxResult / TableDataInfo，列表含 rows、total；详情含 data。请从响应中的 code、msg 判断操作结果。</a-descriptions-item></a-descriptions>
 </a-card><a-card title="在线调用" class="test-card"><a-form layout="vertical" @submit.prevent="run">
 <a-form-item label="接口"><a-select v-model:value="action" :options="OPEN_API_ACTIONS.map(a=>({value:a.key,label:`${a.method} ${a.title}`}))" /></a-form-item>
 <a-form-item label="Token"><a-input-password v-model:value="form.token" autocomplete="off" /></a-form-item>
 <a-form-item v-if="['detail','remove'].includes(action)" label="记录 ID（删除多条用逗号分隔）"><a-input v-model:value="form.id" /></a-form-item>
 <a-form-item v-if="action==='list'" label="Query"><a-input v-model:value="form.query" placeholder="pageNum=1&pageSize=10" /></a-form-item>
 <a-form-item v-if="['add','edit'].includes(action)" label="JSON 正文"><a-textarea v-model:value="form.body" :rows="8" /></a-form-item>
 <a-button v-auth="'package:openapi-docs:query'" type="primary" :loading="loading" html-type="submit">发送请求</a-button>
 </a-form><a-alert v-if="error" type="error" :message="error" showIcon /><a-divider>响应</a-divider><pre>{{ response }}</pre></a-card></div></template>
<script setup lang="ts">
import {reactive,ref} from 'vue';
import {Modal} from 'ant-design-vue';
import {useGlobSetting} from '/@/hooks/setting';
import {OPEN_API_ACTIONS,openApiRequest,prettyJson} from '../package-dev.mjs';
const columns=[{title:'方法',dataIndex:'method'},{title:'接口',dataIndex:'title'},{title:'路径',dataIndex:'path'}];
const action=ref('list'),loading=ref(false),error=ref(''),response=ref('');
const form=reactive({token:'',id:'',query:'pageNum=1&pageSize=10',body:JSON.stringify({appName:'demo',packageCode:'com.example.demo',url:'https://example.com',status:0},null,2)});
const backend=useGlobSetting().apiUrl||'';
async function execute(){loading.value=true;error.value='';try{const request=openApiRequest(action.value,form);const res=await fetch(backend.replace(/\/$/,'')+request.path,{method:request.method,headers:request.headers,body:request.body,credentials:'omit'});const text=await res.text();response.value=`HTTP ${res.status}\n${prettyJson(text)}`;}catch(e:any){error.value=e.message||'请求失败';}finally{loading.value=false;}}
function run(){if(['add','edit','remove'].includes(action.value)){Modal.confirm({title:'确认执行真实数据写入？',content:'该调用会修改当前 Token 所属应用。',onOk:execute});}else execute();}
</script><style scoped>.openapi-docs{padding:16px}.test-card{margin-top:16px}pre{white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.ant-alert,.ant-table-wrapper{margin-bottom:16px}</style>
