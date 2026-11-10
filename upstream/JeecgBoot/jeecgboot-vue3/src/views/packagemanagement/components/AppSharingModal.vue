<template>
  <a-modal
    :open="visible"
    title="转移"
    :width="480"
    :confirmLoading="saving"
    :maskClosable="false"
    okText="确定"
    cancelText="取消"
    @ok="grant"
    @cancel="close"
  >
    <a-alert v-if="targetsError" :message="targetsError" type="error" class="notice" />
    <a-button v-if="targetsError" :loading="targetsLoading" class="notice" @click="loadTargets">重试接收人</a-button>
    <a-form layout="vertical">
      <a-form-item label="接收人" required>
        <a-select
          v-model:value="targetUser"
          :options="targets"
          :loading="targetsLoading"
          :disabled="saving"
          showSearch
          optionFilterProp="label"
          placeholder="选择接收人"
          allowClear
        />
        <a-empty v-if="!targetsLoading && !targetsError && !targets.length" description="暂无符合条件的接收人" />
      </a-form-item>
    </a-form>
    <a-alert v-if="submitError" :message="submitError" type="error" class="notice" />
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { transferApps, transferTargets } from '/@/api/packagemanagement';
  import type { PackageId } from '/@/types/packagemanagement';

  const emit = defineEmits<{ (event: 'success'): void }>();
  const { hasPermission } = usePermission();
  const visible = ref(false),
    saving = ref(false);
  const ids = ref<PackageId[]>([]),
    targetUser = ref<string>(),
    submitError = ref('');
  const targets = ref<Array<{ value: string; label: string }>>([]),
    targetsLoading = ref(false),
    targetsError = ref('');
  let session = 0,
    targetsRequest = 0;
  const allowed = computed(() => visible.value && ids.value.length === 1 && (hasPermission('package:apps:transfer') || hasPermission('*:*:*')));
  const errorText = (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback);
  const current = (version: number) => visible.value && session === version;
  const validId = (id: unknown): id is PackageId =>
    (typeof id === 'string' && /^[1-9]\d*$/.test(id)) || (typeof id === 'number' && Number.isSafeInteger(id) && id > 0);

  function close() {
    session++;
    visible.value = false;
    saving.value = false;
    targetsLoading.value = false;
  }
  async function open(rows: Array<{ id: PackageId; canManage?: boolean }>) {
    close();
    if (
      rows.length !== 1 ||
      rows[0].canManage !== true ||
      !validId(rows[0].id) ||
      !(hasPermission('package:apps:transfer') || hasPermission('*:*:*'))
    )
      return;
    ids.value = [rows[0].id];
    targetUser.value = undefined;
    submitError.value = targetsError.value = '';
    targets.value = [];
    visible.value = true;
    await loadTargets();
  }
  async function loadTargets() {
    const version = session,
      request = ++targetsRequest;
    targetsLoading.value = true;
    targetsError.value = '';
    try {
      const rows = await transferTargets();
      if (current(version) && request === targetsRequest) targets.value = rows.map((row) => ({ value: row.id, label: row.displayName }));
    } catch (error) {
      if (current(version) && request === targetsRequest) {
        targets.value = [];
        targetsError.value = errorText(error, '接收人加载失败，请重试');
      }
    } finally {
      if (current(version) && request === targetsRequest) targetsLoading.value = false;
    }
  }
  async function grant() {
    if (!allowed.value || saving.value) return;
    if (!targetUser.value) {
      submitError.value = '请选择接收人';
      return;
    }
    const version = session,
      appIds = [...ids.value],
      target = targetUser.value;
    saving.value = true;
    submitError.value = '';
    try {
      await transferApps(appIds, target);
      if (!current(version)) return;
      message.success('转移成功');
      emit('success');
      close();
    } catch (error) {
      if (current(version)) submitError.value = errorText(error, '操作失败，请重试');
    } finally {
      if (current(version)) saving.value = false;
    }
  }
  defineExpose({ open });
  onBeforeUnmount(close);
</script>

<style scoped lang="less">
  .notice {
    margin: 12px 0;
  }
</style>
