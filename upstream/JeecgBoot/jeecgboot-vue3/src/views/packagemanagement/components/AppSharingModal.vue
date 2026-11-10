<template>
  <a-modal :open="visible" title="共享应用" :footer="null" :width="900" :maskClosable="false" @cancel="close">
    <a-alert
      type="info"
      showIcon
      class="notice"
      message="创建者保留原有权限；接收者获得应用查看和编辑权限（仍受角色及字段权限限制），不能删除、管理共享或再次转授。共享不会授予日志和关联资源权限。"
    />
    <p>已选择 {{ ids.length }} 个应用。请选择当前租户内已完成身份映射的接收人。</p>
    <a-alert v-if="targetsError" :message="targetsError" type="error" class="notice" />
    <a-space v-if="targetsError" class="notice"><a-button :loading="targetsLoading" @click="loadTargets">重试接收人</a-button></a-space>
    <a-form layout="vertical" @submit.prevent="grant">
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
      <a-form-item label="共享原因" required
        ><a-textarea v-model:value="reason" :disabled="saving" :maxlength="500" showCount :rows="3" placeholder="必填，最多500字符"
      /></a-form-item>
      <a-button
        v-auth="'package:apps:transfer'"
        type="primary"
        :loading="saving && operation === 'grant'"
        :disabled="saving || !allowed"
        @click="grant"
        >共享应用</a-button
      >
    </a-form>
    <a-alert v-if="submitError" :message="submitError" type="error" class="notice" />
    <template v-if="ids.length === 1">
      <a-divider>共享接收者</a-divider>
      <a-alert v-if="sharesError" :message="sharesError" type="error" class="notice" />
      <a-button :loading="sharesLoading" :disabled="saving" class="notice" @click="loadShares(sharesPage)">刷新接收者</a-button>
      <a-table
        :columns="shareColumns"
        :dataSource="shares"
        :loading="sharesLoading"
        rowKey="userId"
        :pagination="{ current: sharesPage, pageSize, total: sharesTotal, showSizeChanger: false }"
        @change="(page) => loadShares(page.current)"
      >
        <template #bodyCell="{ column, record }"
          ><a v-if="column.key === 'action' && allowed" v-auth="'package:apps:transfer'" :aria-disabled="saving" @click="beginRevoke(record)"
            >撤销共享</a
          ></template
        >
      </a-table>
      <a-form v-if="revokeTarget" layout="vertical" class="notice" @submit.prevent="revoke">
        <p>撤销 {{ revokeTarget.displayName || revokeTarget.userId }} 的共享权限</p>
        <a-form-item label="撤销原因" required
          ><a-textarea v-model:value="revokeReason" :disabled="saving" :maxlength="500" showCount :rows="2"
        /></a-form-item>
        <a-space
          ><a-button
            v-auth="'package:apps:transfer'"
            danger
            :loading="saving && operation === 'revoke'"
            :disabled="saving || !allowed"
            @click="revoke"
            >确认撤销</a-button
          ><a-button :disabled="saving" @click="revokeTarget = null">取消撤销</a-button></a-space
        >
      </a-form>
      <a-divider>共享审计</a-divider>
      <a-alert v-if="auditError" :message="auditError" type="error" class="notice" />
      <a-button :loading="auditLoading" :disabled="saving" class="notice" @click="loadAudit(auditPage)">刷新审计</a-button>
      <a-table
        :columns="auditColumns"
        :dataSource="audit"
        :loading="auditLoading"
        rowKey="id"
        :pagination="{ current: auditPage, pageSize, total: auditTotal, showSizeChanger: false }"
        @change="(page) => loadAudit(page.current)"
      >
        <template #bodyCell="{ column, text }"
          ><template v-if="column.dataIndex === 'action'">{{ text === 'GRANT' ? '共享' : text === 'REVOKE' ? '撤销' : text }}</template></template
        >
      </a-table>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { getAppShareAudit, getAppShares, revokeAppShares, shareApps, transferTargets } from '/@/api/packagemanagement';
  import type { AppShareAuditRecord, AppShareRecord } from '/@/api/packagemanagement';
  import type { PackageId } from '/@/types/packagemanagement';

  const emit = defineEmits<{ (event: 'success'): void }>();
  const { hasPermission } = usePermission();
  const visible = ref(false),
    saving = ref(false),
    operation = ref('');
  const ids = ref<PackageId[]>([]),
    targetUser = ref<string>(),
    reason = ref(''),
    submitError = ref('');
  const targets = ref<Array<{ value: string; label: string }>>([]),
    targetsLoading = ref(false),
    targetsError = ref('');
  const shares = ref<AppShareRecord[]>([]),
    sharesTotal = ref(0),
    sharesPage = ref(1),
    sharesLoading = ref(false),
    sharesError = ref('');
  const audit = ref<AppShareAuditRecord[]>([]),
    auditTotal = ref(0),
    auditPage = ref(1),
    auditLoading = ref(false),
    auditError = ref('');
  const revokeTarget = ref<AppShareRecord | null>(null),
    revokeReason = ref('');
  const pageSize = 20;
  let session = 0,
    targetsRequest = 0,
    sharesRequest = 0,
    auditRequest = 0;
  const allowed = computed(() => visible.value && ids.value.length > 0 && (hasPermission('package:apps:transfer') || hasPermission('*:*:*')));
  const shareColumns = [
    { title: '接收者', dataIndex: 'displayName' },
    { title: '用户编号', dataIndex: 'userId' },
    { title: '授权人', dataIndex: 'grantedBy' },
    { title: '原因', dataIndex: 'reason' },
    { title: '共享时间', dataIndex: 'createdAt' },
    { title: '操作', key: 'action' },
  ];
  const auditColumns = [
    { title: '接收者', dataIndex: 'displayName' },
    { title: '接收者编号', dataIndex: 'granteeUserId' },
    { title: '操作人', dataIndex: 'operatorName' },
    { title: '操作', dataIndex: 'action' },
    { title: '原因', dataIndex: 'reason' },
    { title: '时间', dataIndex: 'createdAt' },
  ];
  const errorText = (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback);
  const current = (version: number) => visible.value && session === version;
  const validId = (id: unknown): id is PackageId =>
    (typeof id === 'string' && /^[1-9]\d*$/.test(id)) || (typeof id === 'number' && Number.isSafeInteger(id) && id > 0);

  function close() {
    session++;
    visible.value = false;
    saving.value = false;
    targetsLoading.value = sharesLoading.value = auditLoading.value = false;
  }
  async function open(rows: Array<{ id: PackageId; canManage?: boolean }>) {
    close();
    if (
      !rows.length ||
      rows.some((row) => row.canManage !== true || !validId(row.id)) ||
      !(hasPermission('package:apps:transfer') || hasPermission('*:*:*'))
    )
      return;
    const unique = [...new Map(rows.map((row) => [String(row.id), row.id])).values()];
    if (unique.length > 100) {
      message.warning('一次最多共享100个应用');
      return;
    }
    ids.value = unique;
    targetUser.value = undefined;
    reason.value = submitError.value = targetsError.value = sharesError.value = auditError.value = revokeReason.value = '';
    targets.value = [];
    shares.value = [];
    audit.value = [];
    sharesTotal.value = auditTotal.value = 0;
    sharesPage.value = auditPage.value = 1;
    revokeTarget.value = null;
    operation.value = '';
    visible.value = true;
    await Promise.all([loadTargets(), ...(ids.value.length === 1 ? [loadShares(), loadAudit()] : [])]);
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
  async function loadShares(page = 1) {
    if (!allowed.value || ids.value.length !== 1) return;
    const version = session,
      request = ++sharesRequest;
    sharesPage.value = page;
    sharesLoading.value = true;
    sharesError.value = '';
    try {
      const result = await getAppShares(ids.value[0], { pageNo: page, pageSize });
      if (current(version) && request === sharesRequest) {
        shares.value = result.records;
        sharesTotal.value = result.total;
      }
    } catch (error) {
      if (current(version) && request === sharesRequest) {
        shares.value = [];
        sharesTotal.value = 0;
        sharesError.value = errorText(error, '共享列表加载失败，请重试');
      }
    } finally {
      if (current(version) && request === sharesRequest) sharesLoading.value = false;
    }
  }
  async function loadAudit(page = 1) {
    if (!allowed.value || ids.value.length !== 1) return;
    const version = session,
      request = ++auditRequest;
    auditPage.value = page;
    auditLoading.value = true;
    auditError.value = '';
    try {
      const result = await getAppShareAudit(ids.value[0], { pageNo: page, pageSize });
      if (current(version) && request === auditRequest) {
        audit.value = result.records;
        auditTotal.value = result.total;
      }
    } catch (error) {
      if (current(version) && request === auditRequest) {
        audit.value = [];
        auditTotal.value = 0;
        auditError.value = errorText(error, '共享审计加载失败，请重试');
      }
    } finally {
      if (current(version) && request === auditRequest) auditLoading.value = false;
    }
  }
  function beginRevoke(row: AppShareRecord) {
    if (!allowed.value || saving.value || ids.value.length !== 1) return;
    revokeTarget.value = { ...row };
    revokeReason.value = '';
    submitError.value = '';
  }
  async function write(kind: 'grant' | 'revoke') {
    if (!allowed.value || saving.value) return;
    const target = kind === 'grant' ? targetUser.value : revokeTarget.value?.userId;
    const rawReason = kind === 'grant' ? reason.value : revokeReason.value;
    if (!target || !rawReason.trim() || rawReason.length > 500 || (kind === 'revoke' && ids.value.length !== 1)) {
      submitError.value = '请选择接收人并填写原因（最多500字符）';
      return;
    }
    const version = session,
      appIds = [...ids.value];
    saving.value = true;
    operation.value = kind;
    submitError.value = '';
    try {
      const count = await (kind === 'grant' ? shareApps : revokeAppShares)(appIds, target, rawReason.trim());
      if (!current(version)) return;
      message.success(`${kind === 'grant' ? '共享' : '撤销'}成功，变更 ${count} 条`);
      emit('success');
      if (kind === 'grant') {
        targetUser.value = undefined;
        reason.value = '';
      } else {
        revokeTarget.value = null;
        revokeReason.value = '';
      }
      if (appIds.length === 1) await Promise.all([loadShares(), loadAudit()]);
      else close();
    } catch (error) {
      if (current(version)) submitError.value = errorText(error, '操作失败，请重试');
    } finally {
      if (current(version)) saving.value = false;
    }
  }
  const grant = () => write('grant');
  const revoke = () => write('revoke');
  defineExpose({ open });
  onBeforeUnmount(close);
</script>

<style scoped lang="less">
  .notice {
    margin: 12px 0;
  }
</style>
