<template>
  <a-popover trigger="click" placement="topLeft">
    <template #content
      ><div class="full-url">{{ value }}</div></template
    >
    <button type="button" class="url-text" :title="value" :aria-label="`查看完整地址：${value}`">{{ value }}</button>
  </a-popover>
</template>

<script setup lang="ts">
  defineProps<{ value: string }>();
</script>

<style scoped lang="less">
  .url-text {
    display: block;
    width: 100%;
    min-width: 0;
    padding: 0;
    border: 0;
    background: transparent;
    color: inherit;
    text-align: left;
    cursor: pointer;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .url-text:focus-visible {
    outline: 2px solid #1677ff;
    outline-offset: 3px;
  }
  .full-url {
    max-width: min(560px, 75vw);
    overflow-wrap: anywhere;
    user-select: text;
  }
</style>
