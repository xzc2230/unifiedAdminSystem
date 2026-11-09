<template>
  <a-divider orientation="left">应用标签与评分</a-divider>
  <a-form-item label="应用标签" required>
    <a-space wrap>
      <a-input v-model:value="tagText" placeholder="输入标签后添加" @pressEnter.prevent="addTag" />
      <a-button @click="addTag">添加标签</a-button><a-button @click="tags = []">清空标签</a-button>
      <a-tag v-for="(tag,index) in tags" :key="index" closable @close.prevent="tags.splice(index,1)">{{ tag }}</a-tag>
    </a-space>
  </a-form-item>
  <a-form-item label="评分分布" required>
    <a-row :gutter="16">
      <a-col :span="18"><div v-for="i in [5,4,3,2,1]" :key="i" class="score-line"><span>{{ i }} 星</span><a-slider v-model:value="scores[`score${i}`]" :min="0" :max="100" :step="1" style="flex:1" /><a-input-number v-model:value="scores[`score${i}`]" :min="0" :max="100" /></div></a-col>
      <a-col :span="6"><a-statistic title="加权评分" :value="weightedScore(scores)" /></a-col>
    </a-row>
  </a-form-item>
  <a-divider orientation="left">应用评论</a-divider>
  <a-alert message="已保存的评论删除后立即生效，取消应用编辑也不会恢复。" type="info" showIcon class="review-notice" />
  <a-card v-for="(review,index) in value.reviewsRecords" :key="review.reviewsId || `new-${index}`" :title="`评论 ${index+1}`" size="small" class="review-card">
    <template #extra><a-popconfirm title="确认删除此评论？已保存的评论将立即删除。" @confirm="removeReview(review)"><a-button danger size="small" :loading="pendingRemovals.has(review.reviewsId ?? review)" :disabled="pendingRemovals.has(review.reviewsId ?? review) || Boolean(review.reviewsId) && !hasPermission('package:reviews:remove')">删除评论</a-button></a-popconfirm></template>
    <a-row :gutter="16">
      <a-col :xs="24" :sm="8"><a-form-item label="头像" required><PackageImageUpload v-model:value="review.headPortraitUrl" :fileMax="1" /></a-form-item></a-col>
      <a-col :xs="24" :sm="16">
        <a-form-item label="姓名" required><a-input v-model:value="review.name" /></a-form-item>
        <a-form-item label="评分"><a-input-number v-model:value="review.score" :min="0" :max="5" /></a-form-item>
        <a-form-item label="评论日期" required><a-date-picker v-model:value="review.createTime" showTime valueFormat="YYYY-MM-DD HH:mm:ss" style="width:100%" /></a-form-item>
      </a-col>
    </a-row>
    <a-form-item label="评论内容" required><a-textarea v-model:value="review.content" :rows="3" :maxlength="5000" showCount /></a-form-item>
  </a-card>
  <a-button block @click="addReview">添加评论</a-button>
</template>
<script setup lang="ts">
import { ref,reactive } from 'vue';
import { message } from 'ant-design-vue';
import PackageImageUpload from './PackageImageUpload.vue';
import { usePermission } from '/@/hooks/web/usePermission';
import { packageApi } from '/@/api/packagemanagement';
import { parseTags,parseScore,weightedScore,localDateTime,pwaPayload,createReviewRemover } from '../package-conversions.mjs';
const props = defineProps<{value:Record<string,any>;hosts:Array<{value:any;label:string}>;appType:number}>();
const { hasPermission } = usePermission();
const tags = ref(parseTags(props.value.tags));
const scores = ref(parseScore(props.value.score));
const tagText = ref('');
function addTag() { if(tagText.value.trim()) { tags.value.push(tagText.value.trim()); tagText.value=''; } }
function addReview() { props.value.reviewsRecords.push({name:'',content:'',headPortraitUrl:'',score:5,createTime:localDateTime(new Date())}); }
const pendingRemovals=reactive(new Set());
const removeByIdentity=createReviewRemover(()=>props.value.reviewsRecords,id=>packageApi('reviews').remove([id]),pendingRemovals);
async function removeReview(review:Record<string,any>) {
  const removed=await removeByIdentity(review);
  if(removed && review.reviewsId != null) message.success('评论已立即删除');
}
defineExpose({payload:()=>pwaPayload(props.value,tags.value,scores.value,props.hosts,props.appType)});
</script>
<style scoped>.score-line{display:flex;align-items:center;gap:12px}.review-card,.review-notice{margin-bottom:16px}</style>
