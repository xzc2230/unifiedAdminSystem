<template>
  <BasicModal
    @register="registerModal"
    :title="editingRow ? '编辑应用' : '新建应用'"
    :width="800"
    :can-fullscreen="false"
    :draggable="false"
    :use-wrapper="false"
    :body-style="{ padding: '0' }"
    :mask-closable="false"
    :closable="!saving"
    :keyboard="!saving"
    @cancel="cancel"
  >
    <div ref="bodyRef" class="attribution-modal-body" :aria-busy="loading || saving">
      <a-spin v-if="loading" tip="正在读取应用配置…" class="modal-loading" />
      <a-alert v-else-if="loadError" type="error" show-icon :message="loadError">
        <template #action><a-button size="small" @click="loadConfiguration">重试</a-button></template>
      </a-alert>
      <template v-else-if="ready">
        <a-alert v-if="saveError" type="error" show-icon :message="saveError" class="save-error" />
        <a-alert v-if="capabilityError" type="warning" show-icon message="暂时无法读取可用条件，国家/地区条件保持关闭。" class="save-error">
          <template #action><a-button size="small" @click="loadCapabilities">重试</a-button></template>
        </a-alert>
        <a-form layout="vertical" :model="form" :disabled="saving" @submit.prevent>
          <div class="name-fields">
            <a-form-item
              label="应用名"
              required
              :validate-status="status('applicationName')"
              :help="message('applicationName')"
              data-field="applicationName"
            >
              <a-input v-model:value="form.applicationName" :maxlength="100" placeholder="输入应用名" />
            </a-form-item>
            <a-form-item label="渠道名" required :validate-status="status('channelName')" :help="message('channelName')" data-field="channelName">
              <a-input v-model:value="form.channelName" :maxlength="100" placeholder="输入渠道名" />
            </a-form-item>
          </div>
          <a-form-item label="兜底地址" required :validate-status="status('fallbackUrl')" :help="message('fallbackUrl')" data-field="fallbackUrl">
            <a-input v-model:value="form.fallbackUrl" :maxlength="2048" placeholder="https://example.com/fallback" />
          </a-form-item>
          <p class="form-hint">无可用 Offer 时使用兜底地址。保存后立即生效。</p>
          <div class="offers-heading" data-field="offers">
            <h3
              >Offer 地址 <span class="offer-count">{{ form.offers.length }}</span></h3
            >
            <a-button :disabled="saving" @click="addOffer">+ 添加 Offer</a-button>
          </div>
          <p v-if="message('offers')" class="field-error" role="alert">{{ message('offers') }}</p>
          <div v-if="!form.offers.length" class="empty-offers">尚未添加 Offer，所有点击使用兜底地址。</div>
          <article v-for="(offer, index) in form.offers" :key="offer.clientKey" class="offer-card" :data-field="`offers[${index}]`">
            <header class="offer-header">
              <h4>Offer {{ index + 1 }}</h4>
              <a-button :disabled="saving" type="text" danger :aria-label="`移除 Offer ${index + 1}`" @click="removeOffer(index)">移除</a-button>
            </header>
            <p v-if="message(`offers[${index}]`)" class="field-error" role="alert">{{ message(`offers[${index}]`) }}</p>
            <div class="offer-fields">
              <a-form-item
                label="Offer 地址"
                required
                :validate-status="status(`offers[${index}].targetUrl`)"
                :help="message(`offers[${index}].targetUrl`)"
                :data-field="`offers[${index}].targetUrl`"
              >
                <a-input v-model:value="offer.targetUrl" :maxlength="2048" placeholder="https://example.com/offer" />
              </a-form-item>
              <a-form-item
                label="权重"
                required
                :validate-status="status(`offers[${index}].weight`)"
                :help="message(`offers[${index}].weight`)"
                :data-field="`offers[${index}].weight`"
              >
                <a-input-number v-model:value="offer.weight" :min="0" :precision="0" :max="Number.MAX_SAFE_INTEGER" style="width: 100%" />
              </a-form-item>
            </div>
            <p class="weight-hint">0 权重保留此 Offer，不参与新点击分配。</p>
            <div class="condition-summary">
              <span>{{ conditionSummary(offer) }}</span>
              <a-button
                type="link"
                :disabled="saving"
                :aria-expanded="Boolean(expanded[offer.clientKey])"
                @click="expanded[offer.clientKey] = !expanded[offer.clientKey]"
              >
                {{ expanded[offer.clientKey] ? '收起条件' : '配置条件' }}
              </a-button>
            </div>
            <p v-if="message(`offers[${index}].matchMode`)" class="field-error" role="alert">{{ message(`offers[${index}].matchMode`) }}</p>
            <OfferConditions
              v-if="expanded[offer.clientKey]"
              :offer="offer"
              :country-enabled="countryEnabled"
              :disabled="saving"
              :errors="errors"
              :path="`offers[${index}]`"
              @update:offer="form.offers[index] = $event"
            />
          </article>
        </a-form>
      </template>
    </div>
    <template #footer>
      <a-button :disabled="saving" @click="cancel">取消</a-button>
      <a-button
        :key="editingRow ? 'edit' : 'add'"
        v-auth="editingRow ? 'attribution:application:edit' : 'attribution:application:add'"
        type="primary"
        :loading="saving"
        :disabled="loading || !ready || saving"
        @click="save"
        >保存应用</a-button
      >
    </template>
  </BasicModal>
</template>

<script setup lang="ts">
  import { nextTick, ref, watch } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useMessage } from '/@/hooks/web/useMessage';
  import OfferConditions from './OfferConditions.vue';
  import { getCapabilities, getConfiguration, saveConfiguration } from './attribution.api';
  import { buildPayload, conditionSummary, createSaveAttempt, hydrateForm, newOffer, readError, validateForm } from './attribution.form.mjs';
  import type { ApplicationRow, Configuration, FieldError } from './attribution.types';

  const emit = defineEmits(['register', 'success']);
  const { hasPermission } = usePermission();
  const { createMessage } = useMessage();
  const makeKey = () => crypto.randomUUID();
  const emptyForm = (): Configuration => ({ applicationName: '', channelName: '', fallbackUrl: '', offers: [], managedBindingIds: [] });
  const form = ref<Configuration>(emptyForm());
  const originalOffers = ref<Configuration['offers']>([]);
  const editingRow = ref<ApplicationRow>();
  const bodyRef = ref<HTMLElement>();
  const expanded = ref<Record<string, boolean>>({});
  const errors = ref<FieldError[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const ready = ref(false);
  const loadError = ref('');
  const saveError = ref('');
  const countryEnabled = ref(false);
  const capabilityError = ref(false);
  let session = 0;
  let attempt = createSaveAttempt(makeKey);

  // Observe payload changes synchronously, including edits reverted before the next retry.
  watch(
    () => JSON.stringify(buildPayload(form.value)),
    () => {
      attempt = createSaveAttempt(makeKey);
    },
    { flush: 'sync' }
  );

  const [registerModal, { closeModal }] = useModalInner((data: { row?: ApplicationRow }) => {
    session++;
    editingRow.value = data.row;
    form.value = emptyForm();
    originalOffers.value = [];
    expanded.value = {};
    errors.value = [];
    saveError.value = '';
    loadError.value = '';
    countryEnabled.value = false;
    capabilityError.value = false;
    ready.value = !data.row;
    loading.value = false;
    attempt = createSaveAttempt(makeKey);
    void loadCapabilities();
    if (data.row) void loadConfiguration();
  });

  const message = (path: string) =>
    errors.value
      .filter((error) => error.path === path)
      .map((error) => error.message)
      .join('；');
  const status = (path: string) => (message(path) ? ('error' as const) : undefined);

  async function loadCapabilities() {
    const current = session;
    try {
      const capabilities = await getCapabilities();
      if (current !== session) return;
      countryEnabled.value = capabilities.countryEnabled === true;
      capabilityError.value = false;
    } catch {
      if (current !== session) return;
      countryEnabled.value = false;
      capabilityError.value = true;
    }
  }
  async function loadConfiguration() {
    if (!editingRow.value || loading.value) return;
    const current = session;
    loading.value = true;
    loadError.value = '';
    try {
      const data = await getConfiguration(editingRow.value);
      if (current !== session) return;
      if (typeof data.expectedConfigVersion !== 'string' || !Array.isArray(data.managedBindingIds) || !Array.isArray(data.offers)) {
        throw new Error('应用配置读取不完整，请重试');
      }
      form.value = hydrateForm(data, makeKey);
      originalOffers.value = JSON.parse(JSON.stringify(form.value.offers));
      ready.value = true;
    } catch (error) {
      if (current === session) loadError.value = readError(error).message;
    } finally {
      if (current === session) loading.value = false;
    }
  }
  function addOffer() {
    form.value.offers.push(newOffer(form.value.offers.length, makeKey()));
  }
  function removeOffer(index: number) {
    delete expanded.value[form.value.offers[index].clientKey];
    form.value.offers.splice(index, 1);
    errors.value = [];
  }
  async function locateErrors() {
    for (const error of errors.value) {
      const match = /^offers\[(\d+)\]/.exec(error.path);
      if (match && form.value.offers[Number(match[1])]) expanded.value[form.value.offers[Number(match[1])].clientKey] = true;
    }
    await nextTick();
    const firstPath = errors.value[0]?.path;
    const fields = bodyRef.value?.querySelectorAll<HTMLElement>('[data-field]');
    const target = Array.from(fields ?? [])
      .reverse()
      .find((field) => firstPath?.startsWith(field.dataset.field ?? '\0'));
    target?.scrollIntoView({ block: 'nearest' });
    target?.querySelector<HTMLElement>('input, button, [tabindex]')?.focus({ preventScroll: true });
  }
  async function save() {
    const permission = editingRow.value ? 'attribution:application:edit' : 'attribution:application:add';
    if (saving.value || !ready.value || !hasPermission(permission)) return;
    errors.value = validateForm(form.value, { countryEnabled: countryEnabled.value, originalOffers: originalOffers.value });
    saveError.value = '';
    if (errors.value.length) {
      await locateErrors();
      return;
    }
    const request = attempt.prepare(buildPayload(form.value));
    saving.value = true;
    try {
      await saveConfiguration(editingRow.value, request.payload, request.key);
      createMessage.success('应用已保存');
      closeModal();
      session++;
      emit('success');
    } catch (error) {
      const failure = readError(error, form.value.offers);
      errors.value = failure.fieldErrors;
      saveError.value = failure.conflict ? `配置冲突：${failure.message}。已保留本次输入，请核对后关闭并重新读取最新配置。` : failure.message;
      await locateErrors();
    } finally {
      saving.value = false;
    }
  }
  function cancel() {
    if (saving.value) return;
    session++;
    closeModal();
  }
</script>

<style scoped lang="less">
  .attribution-modal-body {
    max-height: calc(100dvh - 220px);
    min-height: 140px;
    overflow-y: auto;
    padding: 24px;
    overscroll-behavior: contain;
  }
  .modal-loading {
    display: block;
    padding: 40px 0;
  }
  .name-fields {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
  }
  .form-hint,
  .weight-hint {
    color: #66758a;
    font-size: 12px;
  }
  .form-hint {
    margin: -12px 0 24px;
  }
  .offers-heading,
  .offer-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .offers-heading {
    margin-bottom: 14px;
  }
  .offers-heading h3,
  .offer-header h4 {
    margin: 0;
    font-weight: 600;
  }
  .offer-count {
    color: #66758a;
    font-size: 13px;
    margin-left: 6px;
    font-variant-numeric: tabular-nums;
  }
  .offer-card {
    border: 1px solid #dce4ee;
    border-left: 3px solid #1677ff;
    border-radius: 8px;
    padding: 16px;
    margin-top: 16px;
  }
  .offer-header {
    margin-bottom: 12px;
  }
  .offer-fields {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 120px;
    gap: 16px;
  }
  .offer-fields :deep(.ant-form-item) {
    margin-bottom: 8px;
  }
  .weight-hint {
    margin: 0 0 8px;
  }
  .condition-summary {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: baseline;
  }
  .condition-summary > span {
    overflow-wrap: anywhere;
    color: #526174;
    font-size: 13px;
  }
  .condition-summary :deep(.ant-btn) {
    flex-shrink: 0;
    padding-right: 0;
  }
  .empty-offers {
    border: 1px dashed #dce4ee;
    border-radius: 6px;
    color: #66758a;
    padding: 24px 16px;
    text-align: center;
  }
  .field-error {
    color: #cf1322;
    margin: 6px 0 12px;
    font-size: 13px;
  }
  .save-error {
    margin-bottom: 16px;
  }
  @media (max-width: 600px) {
    .attribution-modal-body {
      max-height: calc(100dvh - 130px);
      padding: 16px;
    }
    .name-fields,
    .offer-fields {
      grid-template-columns: minmax(0, 1fr);
      gap: 0;
    }
    .offer-card {
      padding: 12px;
    }
  }
</style>
