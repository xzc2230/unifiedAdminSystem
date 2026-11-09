<template>
  <div>
    <PackageImageUpload v-if="field.type === 'image'" :value="value" :fileMax="field.max || 1" :disabled="disabled" @change="update" />
    <a-select v-else-if="field.type === 'dict' || field.type === 'parent' || field.key === 'status' && domain === 'store'" :value="value" :options="options" :mode="field.multiple ? 'multiple' : undefined" :disabled="disabled || !options.length" allowClear showSearch optionFilterProp="label" style="width:100%" @change="update" />
    <a-switch v-else-if="field.type === 'switch'" :checked="Number(value) === 1" :disabled="disabled" @change="update($event ? 1 : 0)" />
    <a-date-picker v-else-if="field.type === 'date' || field.type === 'datetime'" :value="value || undefined" :showTime="field.type === 'datetime'" :valueFormat="field.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'" :disabled="disabled" style="width:100%" @change="update" />
    <a-input-number v-else-if="field.type === 'number'" :value="value" :min="0" :disabled="disabled" style="width:100%" @change="update" />
    <a-textarea v-else-if="field.type === 'textarea'" :value="value" :rows="3" :maxlength="field.key === 'appDescription' ? 5000 : undefined" :disabled="disabled" @update:value="update" />
    <a-input v-else :value="value" :maxlength="field.key === 'appName' && domain === 'pwa' ? 50 : undefined" :disabled="disabled" allowClear @update:value="update" />
    <div v-if="(field.type === 'dict' || field.type === 'parent') && !options.length" class="empty-hint">{{ field.type === 'parent' ? '没有可选应用，请检查 PWA 查询权限及应用归属' : `字典 ${field.dict} 暂无可用选项，请联系管理员配置` }}</div>
  </div>
</template>
<script setup lang="ts">
import type { PackageField, PackageOption } from '/@/types/packagemanagement';
import PackageImageUpload from './PackageImageUpload.vue';
defineProps<{field:PackageField;value:any;options:PackageOption[];disabled?:boolean;domain?:string}>();
const emit = defineEmits(['update:value']);
const update = value => emit('update:value',value);
</script>
<style scoped>.empty-hint{color:#8c8c8c;font-size:12px;margin-top:4px}</style>
