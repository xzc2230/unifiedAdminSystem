<template>
  <div class="p-4">
    <a-alert v-if="!canQuery" type="warning" show-icon message="无业务账号查询权限" />
    <template v-else>
      <a-form layout="inline" class="identity-filters" @submit.prevent="search">
        <a-form-item label="租户">
          <a-select
            v-model:value="tenantId"
            :options="tenantOptions"
            :loading="tenantsLoading"
            allow-clear
            show-search
            option-filter-prop="label"
            placeholder="请选择目标租户"
            style="width: 240px"
          />
        </a-form-item>
        <a-form-item label="关键词">
          <a-input v-model:value="keyword" allow-clear placeholder="用户名 / 姓名" :maxlength="100" @press-enter="search" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="stateFilter" :options="stateOptions" allow-clear placeholder="全部状态" style="width: 140px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button v-auth="'package:identity:query'" type="primary" :loading="loading" :disabled="tenantId === undefined" @click="search"
              >查询</a-button
            >
            <a-button :disabled="loading" @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
      <a-alert v-if="tenantsError" type="error" show-icon :message="tenantsError" class="mb-3">
        <template #action><a-button size="small" :loading="tenantsLoading" @click="loadTenants">重试租户列表</a-button></template>
      </a-alert>
      <a-alert v-else-if="!tenantsLoading && !tenants.length" type="info" show-icon message="暂无可选择的租户" class="mb-3" />
      <a-alert v-if="listError" type="error" show-icon :message="listError" class="mb-3">
        <template #action><a-button size="small" :loading="loading" @click="loadList">重试</a-button></template>
      </a-alert>
      <BasicTable @register="registerTable" :data-source="rows" :loading="loading" :pagination="{ ...pagination }" @change="changePage">
        <template #tableTitle>
          <span>业务账号管理</span>
          <a-button v-auth="'package:identity:query'" :loading="loading" :disabled="tenantId === undefined" @click="loadList">刷新</a-button>
        </template>
        <template #emptyText>{{ tenantId === undefined ? '请先选择目标租户' : '暂无符合条件的租户成员' }}</template>
        <template #state="{ record }"
          ><a-tag :color="stateColors[record.identityState]">{{ stateLabels[record.identityState] || record.identityState }}</a-tag></template
        >
        <template #action="{ record }"><TableAction :actions="getActions(record)" /></template>
      </BasicTable>
    </template>
    <IdentityModal @register="registerModal" @success="loadList" />
    <IdentityAuditDrawer @register="registerAudit" />
  </div>
</template>

<script lang="ts" setup>
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import type { ActionItem } from '/@/components/Table';
  import { useModal } from '/@/components/Modal';
  import { useDrawer } from '/@/components/Drawer';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { getTenants, getIdentities } from './identity.api';
  import { columns, stateColors, stateLabels, stateOptions } from './identity.data';
  import { createRequestGate, errorText } from './identity.behavior';
  import type { IdentityRow, IdentityState, TenantOption } from './identity.types';
  import IdentityModal from './IdentityModal.vue';
  import IdentityAuditDrawer from './IdentityAuditDrawer.vue';

  defineOptions({ name: 'BusinessIdentityIndex' });

  const { hasPermission } = usePermission();
  const canQuery = computed(() => hasPermission('package:identity:query'));
  const tenantId = ref<number>();
  const keyword = ref('');
  const stateFilter = ref<IdentityState>();
  const applied = reactive({ keyword: '', state: undefined as IdentityState | undefined });
  const tenants = ref<TenantOption[]>([]);
  const tenantsLoading = ref(false);
  const tenantsError = ref('');
  const tenantOptions = computed(() =>
    tenants.value.map((tenant) => ({ value: tenant.tenantId, label: `${tenant.tenantName}（${tenant.tenantId}）` }))
  );
  const rows = ref<IdentityRow[]>([]);
  const loading = ref(false);
  const listError = ref('');
  const pagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: true, pageSizeOptions: ['10', '20', '50', '100'] });
  const listGate = createRequestGate();
  const tenantsGate = createRequestGate();
  const [registerModal, { openModal }] = useModal();
  const [registerAudit, { openDrawer }] = useDrawer();
  const [registerTable] = useTable({
    columns,
    rowKey: 'userId',
    immediate: false,
    useSearchForm: false,
    showTableSetting: false,
    canResize: false,
    bordered: true,
    showIndexColumn: false,
    actionColumn: { title: '操作', dataIndex: 'action', width: 240, fixed: 'right', slots: { customRender: 'action' } },
  });

  async function loadTenants() {
    if (!canQuery.value || tenantsLoading.value) return;
    const request = tenantsGate.next();
    tenantsLoading.value = true;
    tenantsError.value = '';
    try {
      const result = await getTenants();
      if (!tenantsGate.isCurrent(request)) return;
      tenants.value = result;
      if (tenantId.value !== undefined && !result.some((tenant) => tenant.tenantId === tenantId.value)) tenantId.value = undefined;
    } catch (error) {
      if (tenantsGate.isCurrent(request)) tenantsError.value = errorText(error, '租户列表加载失败，请重试');
    } finally {
      if (tenantsGate.isCurrent(request)) tenantsLoading.value = false;
    }
  }

  async function loadList() {
    const request = listGate.next();
    rows.value = [];
    pagination.total = 0;
    listError.value = '';
    loading.value = false;
    if (tenantId.value === undefined || !canQuery.value) return;
    const params = {
      tenantId: tenantId.value,
      keyword: applied.keyword,
      state: applied.state,
      pageNo: pagination.current,
      pageSize: pagination.pageSize,
    };
    loading.value = true;
    try {
      const result = await getIdentities(params);
      if (!listGate.isCurrent(request)) return;
      const last = Math.max(1, Math.ceil(result.total / pagination.pageSize));
      if (pagination.current > last) {
        pagination.current = last;
        return await loadList();
      }
      rows.value = result.records;
      pagination.total = result.total;
    } catch (error) {
      if (listGate.isCurrent(request)) listError.value = errorText(error, '成员列表加载失败，请重试');
    } finally {
      if (listGate.isCurrent(request)) loading.value = false;
    }
  }

  function search() {
    applied.keyword = keyword.value.trim();
    applied.state = stateFilter.value;
    pagination.current = 1;
    return loadList();
  }
  function reset() {
    keyword.value = '';
    stateFilter.value = undefined;
    return search();
  }
  function changePage(page: { current?: number; pageSize?: number }) {
    const size = page.pageSize || pagination.pageSize;
    pagination.current = size === pagination.pageSize ? page.current || 1 : 1;
    pagination.pageSize = size;
    return loadList();
  }
  function showOperation(mode: 'open' | 'bind' | 'status', record: IdentityRow) {
    const permission = mode === 'status' ? 'status' : mode;
    if (
      !targetValid(record) ||
      loading.value ||
      record.tenantId !== tenantId.value ||
      !rows.value.includes(record) ||
      !hasPermission(`package:identity:${permission}`)
    )
      return;
    openModal(true, { mode, record: { ...record } });
  }
  function targetValid(record: IdentityRow) {
    return String(record.userStatus) === '1' && String(record.memberStatus) === '1' && record.tenantActive === true;
  }
  function getActions(record: IdentityRow): ActionItem[] {
    const writeDisabled = loading.value || !targetValid(record);
    return [
      {
        label: '开通',
        auth: 'package:identity:open',
        ifShow: record.identityState === 'UNOPENED',
        disabled: writeDisabled,
        onClick: () => showOperation('open', record),
      },
      {
        label: '历史绑定',
        auth: 'package:identity:bind',
        ifShow: record.identityState === 'UNOPENED',
        disabled: writeDisabled,
        onClick: () => showOperation('bind', record),
      },
      {
        label: record.identityState === 'DISABLED' ? '恢复' : '停用',
        auth: 'package:identity:status',
        ifShow: ['ACTIVE', 'DISABLED'].includes(record.identityState),
        disabled: writeDisabled,
        onClick: () => showOperation('status', record),
      },
      {
        label: '操作记录',
        auth: 'package:identity:query',
        disabled: loading.value,
        onClick: () => {
          if (!loading.value && hasPermission('package:identity:query') && record.tenantId === tenantId.value && rows.value.includes(record)) {
            openDrawer(true, { record: { ...record } });
          }
        },
      },
    ];
  }
  watch(
    tenantId,
    () => {
      pagination.current = 1;
      void loadList();
    },
    { flush: 'sync' }
  );
  onMounted(loadTenants);
  onBeforeUnmount(() => {
    listGate.invalidate();
    tenantsGate.invalidate();
  });
</script>

<style scoped lang="less">
  .identity-filters {
    padding: 16px;
    margin-bottom: 12px;
    background: @component-background;
    gap: 12px 0;
  }
</style>
