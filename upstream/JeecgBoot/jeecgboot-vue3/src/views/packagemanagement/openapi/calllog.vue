<template><DevRecordPage title="OpenAPI 调用日志" path="openapi/calllog" domain="openapi-calllog" readonly :fields="fields" :filters="filters" /></template>
<script setup lang="ts">
import DevRecordPage from '../components/DevRecordPage.vue';
const fields=[{key:'apiPath',label:'接口路径',width:240},{key:'requestBody',label:'请求参数',json:true},{key:'responseBody',label:'响应参数',json:true},{key:'token',label:'Token'},{key:'callTime',label:'调用时间',width:180}];
const filters=[{field:'apiPath',label:'接口路径',component:'Input',colProps:{span:6}},{field:'token',label:'Token',component:'Input',colProps:{span:6}},{field:'beginTime',label:'开始日期',component:'DatePicker',componentProps:{valueFormat:'YYYY-MM-DD'},colProps:{span:6}},{field:'endTime',label:'结束日期',component:'DatePicker',componentProps:{valueFormat:'YYYY-MM-DD'},colProps:{span:6}}];
</script>
