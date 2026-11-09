<template>
  <BasicDrawer v-bind="$attrs" @register="registerDrawer" title="业务账号操作记录" width="1000px" @visible-change="onVisibleChange">
    <a-descriptions v-if="record" :column="1" size="small" class="mb-3">
      <a-descriptions-item label="租户">{{ record.tenantName }}</a-descriptions-item>
      <a-descriptions-item label="用户">{{ record.realname || record.username }}（{{ record.username }}）</a-descriptions-item>
    </a-descriptions>
    <a-alert v-if="auditError" type="error" show-icon :message="auditError" class="mb-3">
      <template #action><a-button :loading="loading" size="small" @click="loadAudit">重试</a-button></template>
    </a-alert>
    <BasicTable @register="registerTable" :data-source="rows" :loading="loading" :pagination="{ ...pagination }" @change="changePage">
      <template #tableTitle><a-button v-auth="'package:identity:query'" :loading="loading" @click="loadAudit">刷新</a-button></template>
    </BasicTable>
  </BasicDrawer>
</template>

<script lang="ts" setup>
  import { onBeforeUnmount, reactive, ref } from 'vue';
  import { BasicDrawer, useDrawerInner } from '/@/components/Drawer';
  import { BasicTable, useTable } from '/@/components/Table';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { getIdentityAudit } from './identity.api';
  import { auditColumns } from './identity.data';
  import { createRequestGate, errorText } from './identity.behavior';
  import type { AuditRow, IdentityRow } from './identity.types';

  defineEmits(['register']);
  const { hasPermission } = usePermission();
  const record = ref<IdentityRow | null>(null);
  const active = ref(false);
  const rows = ref<AuditRow[]>([]);
  const loading = ref(false);
  const auditError = ref('');
  const gate = createRequestGate();
  const pagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: true });
  const [registerTable] = useTable({
    columns: auditColumns,
    rowKey: 'id',
    immediate: false,
    useSearchForm: false,
    showTableSetting: false,
    showIndexColumn: false,
    canResize: false,
    bordered: true,
  });
  const [registerDrawer] = useDrawerInner((data: { record: IdentityRow }) => {
    record.value = { ...data.record };
    active.value = true;
    pagination.current = 1;
    void loadAudit();
  });

  async function loadAudit() {
    const request = gate.next();
    rows.value = [];
    pagination.total = 0;
    auditError.value = '';
    loading.value = false;
    if (!record.value || !active.value || !hasPermission('package:identity:query')) return;
    const params = { tenantId: record.value.tenantId, userId: record.value.userId, pageNo: pagination.current, pageSize: pagination.pageSize };
    loading.value = true;
    try {
      const result = await getIdentityAudit(params);
      if (!gate.isCurrent(request)) return;
      const last = Math.max(1, Math.ceil(result.total / pagination.pageSize));
      if (pagination.current > last) {
        pagination.current = last;
        return await loadAudit();
      }
      rows.value = result.records;
      pagination.total = result.total;
    } catch (error) {
      if (gate.isCurrent(request)) auditError.value = errorText(error, '操作记录加载失败，请重试');
    } finally {
      if (gate.isCurrent(request)) loading.value = false;
    }
  }
  function changePage(page: { current?: number; pageSize?: number }) {
    const size = page.pageSize || pagination.pageSize;
    pagination.current = size === pagination.pageSize ? page.current || 1 : 1;
    pagination.pageSize = size;
    return loadAudit();
  }
  function onVisibleChange(visible: boolean) {
    if (!visible) {
      active.value = false;
      gate.invalidate();
      rows.value = [];
      loading.value = false;
    }
  }
  onBeforeUnmount(() => {
    active.value = false;
    gate.invalidate();
  });
</script>
