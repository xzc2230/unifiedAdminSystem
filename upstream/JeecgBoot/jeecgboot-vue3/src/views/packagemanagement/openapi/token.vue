<template><DevRecordPage title="OpenAPI Token" path="openapi/token" domain="openapi-token" :fields="fields" :filters="filters" :defaults="{status:1}" /></template>
<script setup lang="ts">
import DevRecordPage from '../components/DevRecordPage.vue';
const fields=[{key:'token',label:'Token',placeholder:'留空由服务端生成',width:260},{key:'expireTime',label:'有效期',type:'datetime'},{key:'status',label:'状态',options:[{value:1,label:'启用'},{value:0,label:'停用'}]},{key:'remark',label:'备注',type:'textarea'},{key:'createTime',label:'创建时间',readonly:true}];
const filters=[{field:'token',label:'Token',component:'Input',colProps:{span:8}},{field:'status',label:'状态',component:'Select',componentProps:{options:[{value:1,label:'启用'},{value:0,label:'停用'}]},colProps:{span:8}}];
</script>
