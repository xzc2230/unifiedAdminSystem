<template>
  <a-upload :fileList="files" listType="picture-card" accept="image/*" :multiple="fileMax > 1" :maxCount="fileMax" :action="action" :headers="headers" :disabled="disabled" :beforeUpload="beforeUpload" @change="change" @preview="preview">
    <div v-if="!disabled && files.length < fileMax">＋<div>上传图片</div></div>
  </a-upload>
  <a-modal v-model:open="previewOpen" :footer="null"><img :src="previewUrl" alt="图片预览" style="width:100%" /></a-modal>
</template>
<script setup lang="ts">
import {ref,watch} from 'vue';
import {message,Upload} from 'ant-design-vue';
import {useGlobSetting} from '/@/hooks/setting';
import {getHeaders} from '/@/utils/common/compUtils';
import {legacyImageUrl,uploadedImageName} from '../package-conversions.mjs';
const props=withDefaults(defineProps<{value?:string;fileMax?:number;disabled?:boolean}>(),{fileMax:1});
const emit=defineEmits(['update:value','change']);
const backend=useGlobSetting().domainUrl;
const action=`${backend.replace(/\/$/,'')}/package-legacy/common/upload`;
const headers=getHeaders();
const files=ref<any[]>([]),previewOpen=ref(false),previewUrl=ref('');
watch(()=>props.value,value=>{
  const stored=files.value.filter(f=>f.status==='done').map(f=>f.storedName).join(',');
  if(stored===(value || ''))return;
  files.value=String(value || '').split(',').filter(Boolean).map((name,index)=>({uid:`saved-${index}`,name:name.split('/').pop(),status:'done',storedName:name,url:legacyImageUrl(name,backend)}));
},{immediate:true});
function beforeUpload(file:File){
  if(!file.type.startsWith('image/')){message.error('请选择图片文件');return Upload.LIST_IGNORE;}
  if(file.size>10*1024*1024){message.error('图片不能超过 10MB');return Upload.LIST_IGNORE;}
  return true;
}
function change(info:any){
  files.value=info.fileList;
  const file=info.file;
  if(file.status==='done'){
    try{const name=uploadedImageName(file.response);const target=files.value.find(f=>f.uid===file.uid);if(target){target.storedName=name;target.url=legacyImageUrl(name,backend);}}
    catch(error:any){files.value=files.value.filter(f=>f.uid!==file.uid);message.error(error.message);}
  }else if(file.status==='error'){message.error('图片上传失败，请重试');}
  const value=files.value.filter(f=>f.status==='done'&&f.storedName).map(f=>f.storedName).join(',');
  emit('update:value',value);emit('change',value);
}
function preview(file:any){previewUrl.value=file.url || legacyImageUrl(file.storedName,backend);previewOpen.value=true;}
</script>
