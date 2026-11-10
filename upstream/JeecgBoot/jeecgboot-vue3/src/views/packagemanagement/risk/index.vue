<template><a-tabs v-model:activeKey="tab" class="risk-tabs"><a-tab-pane v-for="group in groups" :key="group.key" :tab="group.title"><DevRecordPage :key="group.key" :title="group.title" :path="`risk/${group.key}`" domain="risk" :fields="group.fields" :filters="group.filters" :defaults="{enabled:1,priority:0,matchMode:'contains',caseSensitive:0,matchType:'asn_exact',action:'block'}" /></a-tab-pane></a-tabs></template>
<script setup lang="ts">
import {ref} from 'vue';
import DevRecordPage from '../components/DevRecordPage.vue';
const tab=ref('asn');
const common=[{key:'enabled',label:'启用',type:'number'},{key:'priority',label:'优先级',type:'number'},{key:'remark',label:'备注',type:'textarea'},{key:'updateTime',label:'更新时间',readonly:true}];
const groups=[{key:'asn',title:'ASN 黑名单',field:'asn',label:'ASN',extra:[{key:'asnName',label:'ASN组织',readonly:true}]},
{key:'brand',title:'设备品牌黑名单',field:'brandKeyword',label:'品牌关键字',extra:[]},
{key:'abi',title:'CPU ABI 黑名单',field:'abiKeyword',label:'ABI关键字',extra:[]},
{key:'device',title:'设备型号黑名单',field:'deviceNameKeyword',label:'型号关键字',extra:[]}].map(g=>({...g,fields:[{key:g.field,label:g.label,required:true},...g.extra,...(g.key==='asn'?[{key:'matchType',label:'匹配类型',options:[{value:'asn_exact',label:'ASN 精确匹配'}]}]:[{key:'matchMode',label:'匹配模式',options:[{value:'contains',label:'包含'},{value:'equals',label:'相等'},{value:'prefix',label:'前缀'},{value:'suffix',label:'后缀'}]},{key:'caseSensitive',label:'区分大小写',options:[{value:0,label:'否'},{value:1,label:'是'}]}]),{key:'action',label:'动作',options:[{value:'block',label:'拦截'},{value:'observe',label:'观察'}]},...common],filters:[{field:g.field,label:g.label,component:'Input',colProps:{span:8}}]}));
</script><style scoped>.risk-tabs{padding:16px}</style>
