<template>
  <BasicModal
    v-bind="$attrs"
    @register="registerModal"
    :title="title"
    :width="700"
    :can-fullscreen="false"
    :confirm-loading="saving"
    :ok-button-props="{ disabled: !canSubmit }"
    :cancel-button-props="{ disabled: saving }"
    :close-func="canClose"
    :mask-closable="false"
    :keyboard="!saving"
    @visible-change="onVisibleChange"
    @ok="submit"
  >
    <a-descriptions v-if="record" :column="1" bordered size="small" class="mb-4">
      <a-descriptions-item label="目标租户">{{ record.tenantName }}</a-descriptions-item>
      <a-descriptions-item label="目标用户">{{ record.realname || record.username }}（{{ record.username }}）</a-descriptions-item>
      <a-descriptions-item v-if="mode === 'status'" label="业务账号编号">{{ record.legacyUserId }}</a-descriptions-item>
    </a-descriptions>
    <a-alert
      v-if="mode === 'open'"
      type="info"
      show-icon
      message="系统将自动分配业务账号编号"
      description="开通后不会自动授予应用菜单，也不会取得历史数据。"
      class="mb-4"
    />
    <a-alert
      v-if="mode === 'status'"
      :type="enabled ? 'info' : 'warning'"
      show-icon
      :message="enabled ? '确认恢复此业务账号？' : '确认停用此业务账号？'"
      :description="enabled ? '恢复后可继续使用已有业务账号。' : '停用后将无法继续访问业务，账号及已有数据会保留。'"
      class="mb-4"
    />
    <a-alert
      v-if="mode === 'bind'"
      type="warning"
      show-icon
      message="请先人工核实历史账号编号及归属"
      description="绑定不会更改已登记的资源归属。预检仅供核对，提交时系统会重新检查。"
      class="mb-4"
    />
    <a-form layout="vertical">
      <a-form-item v-if="mode === 'bind'" label="历史账号编号" required :validate-status="idError ? 'error' : undefined" :help="idError">
        <a-input v-model:value="form.legacyUserId" :disabled="saving" placeholder="请输入已核实的历史账号编号" autocomplete="off" />
      </a-form-item>
      <a-form-item label="操作原因" required :validate-status="reasonError ? 'error' : undefined" :help="reasonError">
        <a-textarea
          v-model:value="form.reason"
          :disabled="saving"
          :maxlength="500"
          show-count
          :rows="3"
          placeholder="请填写原因（不超过500个字符）"
        />
      </a-form-item>
      <template v-if="mode === 'bind'">
        <a-button
          v-auth="'package:identity:bind'"
          :loading="previewLoading"
          :disabled="saving || !!validateLegacyId(form.legacyUserId)"
          @click="loadPreview"
          >预检历史账号</a-button
        >
        <a-alert v-if="previewError" type="error" show-icon :message="previewError" class="mt-3" />
        <div v-if="preview" class="mt-3">
          <a-alert
            :type="previewAllowed ? 'success' : 'error'"
            show-icon
            :message="previewAllowed ? '预检通过，请核对以下记录并确认编号' : '预检未通过，不能绑定'"
          />
          <a-descriptions :column="1" bordered size="small" class="mt-3">
            <a-descriptions-item label="历史账号编号">{{ preview.legacyUserId }}</a-descriptions-item>
            <a-descriptions-item label="编号可用">{{ preview.available ? '是' : '否' }}</a-descriptions-item>
            <a-descriptions-item label="编号冲突">{{ preview.conflict || '无' }}</a-descriptions-item>
            <a-descriptions-item label="资源归属冲突数">{{ ownershipConflictCount }}</a-descriptions-item>
          </a-descriptions>
          <a-table
            class="mt-3"
            size="small"
            :pagination="false"
            :data-source="preview.resourceCounts"
            row-key="resourceType"
            :columns="resourceColumns"
          >
            <template #bodyCell="{ column, record: resource }">
              <template v-if="column.dataIndex === 'resourceType'">{{ resourceLabels[resource.resourceType] || '其他业务记录' }}</template>
              <template v-else>{{ resource[column.dataIndex] }}</template>
            </template>
          </a-table>
          <a-checkbox v-model:checked="confirmed" class="mt-3" :disabled="saving || !previewAllowed">
            我已人工核实并确认将历史账号 {{ form.legacyUserId }} 绑定给上述租户用户
          </a-checkbox>
        </div>
      </template>
    </a-form>
    <a-alert v-if="submitError" type="error" show-icon :message="submitError" class="mt-3" />
  </BasicModal>
</template>

<script lang="ts" setup>
  import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { bindIdentity, changeIdentityStatus, getLegacyPreview, openIdentity } from './identity.api';
  import { createPreviewGuard, createRequestGate, createSubmissionLock, errorText, validateLegacyId, validateReason } from './identity.behavior';
  import { resourceLabels } from './identity.data';
  import type { IdentityRow, LegacyPreview } from './identity.types';

  const emit = defineEmits(['register', 'success']);
  const { hasPermission } = usePermission();
  const { createMessage } = useMessage();
  const mode = ref<'open' | 'bind' | 'status'>('open');
  const record = ref<IdentityRow | null>(null);
  const form = reactive({ legacyUserId: '', reason: '' });
  const confirmed = ref(false);
  const saving = ref(false);
  const preview = ref<LegacyPreview | null>(null);
  const previewLoading = ref(false);
  const previewError = ref('');
  const submitError = ref('');
  const idError = ref('');
  const reasonError = ref('');
  const active = ref(false);
  const previewGuard = createPreviewGuard();
  const previewGate = createRequestGate();
  const sessionGate = createRequestGate();
  const submitLock = createSubmissionLock();
  const enabled = computed(() => record.value?.identityState === 'DISABLED');
  const title = computed(() =>
    mode.value === 'bind' ? '绑定历史账号' : mode.value === 'open' ? '开通业务账号' : enabled.value ? '恢复业务账号' : '停用业务账号'
  );
  const permission = computed(() => `package:identity:${mode.value === 'status' ? 'status' : mode.value}`);
  const target = () => ({ tenantId: record.value!.tenantId, userId: record.value!.userId, legacyUserId: form.legacyUserId });
  const previewAllowed = computed(() => !!preview.value && !!record.value && previewGuard.canBind(target()));
  const ownershipConflictCount = computed(() => {
    const conflicts = preview.value?.ownershipConflicts;
    return Array.isArray(conflicts) ? conflicts.length : (conflicts ?? 0);
  });
  const canSubmit = computed(
    () =>
      active.value &&
      !saving.value &&
      !previewLoading.value &&
      hasPermission(permission.value) &&
      !!record.value &&
      !validateReason(form.reason) &&
      (mode.value !== 'bind' || (previewAllowed.value && confirmed.value))
  );
  const resourceColumns = [
    { title: '历史记录类型', dataIndex: 'resourceType' },
    { title: '记录数', dataIndex: 'count' },
  ];

  function invalidatePreview() {
    previewGuard.invalidate();
    previewGate.invalidate();
    preview.value = null;
    previewLoading.value = false;
    previewError.value = '';
    confirmed.value = false;
  }
  watch(
    () => [record.value?.tenantId, record.value?.userId, form.legacyUserId, form.reason],
    () => {
      invalidatePreview();
      idError.value = '';
      reasonError.value = '';
      submitError.value = '';
    },
    { flush: 'sync' }
  );

  const [registerModal, { closeModal }] = useModalInner((data: { mode: typeof mode.value; record: IdentityRow }) => {
    if (saving.value) return;
    sessionGate.next();
    invalidatePreview();
    mode.value = data.mode;
    record.value = { ...data.record };
    form.legacyUserId = '';
    form.reason = '';
    submitError.value = '';
    idError.value = '';
    reasonError.value = '';
    active.value = true;
  });

  async function loadPreview() {
    if (!active.value || saving.value || previewLoading.value || mode.value !== 'bind' || !hasPermission(permission.value) || !record.value) return;
    idError.value = validateLegacyId(form.legacyUserId);
    if (idError.value) return;
    invalidatePreview();
    const params = target();
    const ticket = previewGuard.begin(params);
    const request = previewGate.next();
    previewLoading.value = true;
    try {
      const result = await getLegacyPreview(params);
      if (!previewGate.isCurrent(request) || !active.value) return;
      if (previewGuard.accept(ticket, result)) preview.value = result;
    } catch (error) {
      if (previewGate.isCurrent(request)) previewError.value = errorText(error, '历史账号预检失败，请重试');
    } finally {
      if (previewGate.isCurrent(request)) previewLoading.value = false;
    }
  }

  async function submit() {
    if (!active.value || saving.value || !record.value || !hasPermission(permission.value)) return;
    reasonError.value = validateReason(form.reason);
    idError.value = mode.value === 'bind' ? validateLegacyId(form.legacyUserId) : '';
    if (reasonError.value || idError.value) return;
    if (mode.value === 'bind' && (!previewAllowed.value || !confirmed.value)) {
      submitError.value = '请完成当前历史账号预检，并勾选编号确认';
      return;
    }
    if (mode.value === 'status' && !['ACTIVE', 'DISABLED'].includes(record.value.identityState)) return;
    if (mode.value !== 'status' && record.value.identityState !== 'UNOPENED') return;
    if (!submitLock.acquire()) return;
    const session = sessionGate.next();
    const params = { tenantId: record.value.tenantId, userId: record.value.userId, reason: form.reason.trim() };
    saving.value = true;
    submitError.value = '';
    try {
      if (mode.value === 'open') await openIdentity(params);
      else if (mode.value === 'bind') await bindIdentity({ ...params, legacyUserId: form.legacyUserId });
      else await changeIdentityStatus({ ...params, enabled: enabled.value });
      if (!sessionGate.isCurrent(session) || !active.value) return;
      createMessage.success('操作成功');
      active.value = false;
      invalidatePreview();
      closeModal();
      emit('success');
    } catch (error) {
      if (!sessionGate.isCurrent(session) || !active.value) return;
      submitError.value = errorText(error, '操作失败，请重试');
      // A rejected write may have observed newer ownership; require a new preview and confirmation.
      if (mode.value === 'bind') invalidatePreview();
    } finally {
      submitLock.release();
      saving.value = false;
    }
  }
  async function canClose() {
    return !saving.value;
  }
  function onVisibleChange(visible: boolean) {
    if (!visible) {
      active.value = false;
      sessionGate.invalidate();
      invalidatePreview();
    }
  }
  onBeforeUnmount(() => {
    active.value = false;
    sessionGate.invalidate();
    invalidatePreview();
  });
</script>
