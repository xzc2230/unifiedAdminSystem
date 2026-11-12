<template>
  <DevRecordPage ref="page" title="包统计" path="packstat" domain="packstat" :fields="fields" :filters="filters" :defaults="defaults" :refreshChoices="loadChoices" @query="queryChanged" @changed="loadChoicesAndSummary">
    <template #summary><a-card v-if="summaryQuery.packer" title="包存活 / 使用时间分布" size="small" class="summary"><a-alert v-if="summaryError" :message="summaryError" type="error" /><a-table :columns="summaryColumns" :dataSource="summaryRows(summary)" :loading="summaryLoading" :pagination="false" rowKey="metric" size="small" bordered /><p>7～14 天为独立分桶，15 天包含 15 天及以上。</p></a-card></template>
  </DevRecordPage>
</template>
<script setup lang="ts">
defineOptions({ name: "Packagepackstat" });
import {computed,onMounted,ref} from 'vue';
import {packstatAuthors,packstatRegions,packstatSummary} from '/@/api/packagemanagement/dev';
import DevRecordPage from '../components/DevRecordPage.vue';
import {SURVIVE_DAY_KEYS,summaryRows} from '../package-dev.mjs';
const page=ref(),authors=ref<string[]>([]),regions=ref<any[]>([]),summaryQuery=ref<Record<string,any>>({}),summary=ref<any>({noRecord:true}),summaryLoading=ref(false),summaryError=ref('');
const day=new Date();const today=`${day.getFullYear()}-${String(day.getMonth()+1).padStart(2,'0')}-${String(day.getDate()).padStart(2,'0')}`;
const defaults={platform:'android',isReplenish:0,passDate:today,customerUseDate:today};
const fields=computed(()=>[
 {key:'platform',label:'系统',required:true,options:[{value:'android',label:'Android'},{value:'ios',label:'iOS'}]},
 {key:'storeUrl',label:'商店链接',required:true,width:240},{key:'regionCode',label:'上架地区',required:true,options:regions.value.map(r=>({value:r.code,label:`${r.nameCn||r.name_cn||r.regionName||''} (${r.code})`}))},
 {key:'customer',label:'客户'},{key:'isReplenish',label:'是否补包',required:true,options:[{value:0,label:'否'},{value:1,label:'是'}]},{key:'replenishUrl',label:'补包链接'},
 {key:'packer',label:'出包人',required:true},{key:'notifyEmail',label:'通知邮箱'},
 {key:'passDate',label:'过包日期',type:'date',required:true},{key:'customerUseDate',label:'客户使用日期',type:'date',required:true},{key:'dropDate',label:'掉包日期',type:'date'},
 {key:'aliveDays',label:'存活时长',readonly:true},{key:'useDays',label:'使用时长',readonly:true},{key:'designer',label:'美工'},{key:'remark',label:'备注',type:'textarea'},
]);
const filters=computed(()=>[{field:'packer',label:'出包人',component:'Select',componentProps:{options:authors.value.map(a=>({value:a,label:a})),showSearch:true,allowClear:true},colProps:{span:8}},{field:'passMonth',label:'过包月份',component:'DatePicker',componentProps:{picker:'month',valueFormat:'YYYY-MM'},colProps:{span:8}}]);
const summaryColumns=[{title:'指标',key:'metric',dataIndex:'metric'},...SURVIVE_DAY_KEYS.map(d=>({title:d===15?'15天及以上':`${d}天`,key:'d'+d,dataIndex:'d'+d}))];
let generation=0;
async function queryChanged(query:any){summaryQuery.value={packer:query.packer,passMonth:query.passMonth};await loadSummary();}
async function loadSummary(){const version=++generation;if(!summaryQuery.value.packer){summary.value={noRecord:true};return;}summaryLoading.value=true;summaryError.value='';try{const value=await packstatSummary(summaryQuery.value);if(version===generation)summary.value=value;}catch{if(version===generation){summary.value={noRecord:true};summaryError.value='统计加载失败，请重试';}}finally{if(version===generation)summaryLoading.value=false;}}
async function loadChoices(){const [a,r]=await Promise.all([packstatAuthors(),packstatRegions()]);authors.value=(a||[]).map(v=>typeof v==='string'?v:v.packer||v.author||v.name).filter(Boolean);regions.value=r||[];}
async function loadChoicesAndSummary(){await loadChoices();await loadSummary();}
onMounted(loadChoicesAndSummary);
</script><style scoped>.summary{margin-bottom:16px}.summary p{margin:10px 0 0;color:#666}</style>
