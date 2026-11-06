<template>
  <div class="routing-panel">
    <a-alert
      type="info"
      show-icon
      message="草稿不影响已发布流量。域名须先完成 DNS 验证；本页不会自动签发证书或配置运行节点。"
      description="发布仅更新期望版本；公网 DNS、证书与节点部署需另行配置。ACK 是节点曾应用该版本的记录，不能证明节点此刻健康。"
    />
    <a-alert v-if="!canRead" type="warning" show-icon message="暂无分流与发布读取权限" />
    <template v-else>
      <a-space wrap>
        <a-popconfirm v-if="dirty" title="刷新将丢弃尚未保存的编辑，确认继续？" :disabled="loading || !!busy" @confirm="load">
          <a-button :loading="loading" :disabled="!!busy">刷新草稿与发布记录</a-button>
        </a-popconfirm>
        <a-button v-else :loading="loading" :disabled="!!busy" @click="load">刷新草稿与发布记录</a-button>
        <a-tag v-if="ready">{{ state.draft ? statusLabels[state.draft.status] : '尚无草稿' }}</a-tag>
        <span v-if="ready">版本 revision：{{ input.revision }}</span>
        <a-tag v-if="dirty" color="orange">有未保存修改</a-tag>
      </a-space>
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-alert v-if="notice" type="success" show-icon :message="notice" />
      <a-spin :spinning="loading || !!busy">
        <template v-if="ready">
          <a-descriptions size="small" :column="2" bordered>
            <a-descriptions-item label="提交人 ID">{{ state.draft?.submittedBy || '—' }}</a-descriptions-item>
            <a-descriptions-item label="审批人 ID">{{ state.draft?.approvedBy || '—' }}</a-descriptions-item>
          </a-descriptions>
          <a-alert
            v-if="selfApprovalBlocked"
            type="warning"
            show-icon
            message="生产环境禁止提交人审批自己的变更，请由另一位具备审批权限的站点管理员审批。"
          />
          <a-alert
            v-if="dirty && state.draft && state.draft.status !== 'DRAFT'"
            type="warning"
            show-icon
            message="保存修改会清空当前审批与候选快照，需要重新提交审批。"
          />
          <a-card title="分流草稿" size="small">
            <BasicForm :schemas="draftSchemas" :show-action-button-group="false" :base-col-props="{ span: 24 }" :label-width="100">
              <template #defaultTarget>
                <a-select
                  v-model:value="input.defaultTargetId"
                  aria-label="默认目标"
                  :options="targetOptions"
                  :disabled="!canWrite || locked"
                  placeholder="选择已启用的目标地址"
                  show-search
                  option-filter-prop="label"
                />
              </template>
              <template #domains>
                <a-checkbox-group v-model:value="input.domainIds" :disabled="!canWrite || locked" aria-label="接入域名">
                  <a-space wrap>
                    <a-checkbox
                      v-for="domain in domains"
                      :key="domain.id"
                      :value="domain.id"
                      :disabled="!flagOn(domain.verified) || (input.domainIds.length >= MAX_ROUTING_ITEMS && !input.domainIds.includes(domain.id))"
                    >
                      {{ domain.hostname }}{{ flagOn(domain.verified) ? '' : '（待验证）' }}
                    </a-checkbox>
                  </a-space>
                </a-checkbox-group>
                <span v-if="!domains.length" class="routing-note">暂无域名，请先在“域名与验证”中绑定并验证。</span>
                <div v-if="missingDomainIds.length" class="routing-note"
                  >草稿引用了已不可用的域名：{{ missingDomainIds.join('、') }}。请重新选择并保存。</div
                >
                <a-button v-if="missingDomainIds.length && canWrite" size="small" :disabled="locked" @click="removeMissingDomains"
                  >清除不可用域名引用</a-button
                >
              </template>
            </BasicForm>
            <p class="routing-note"
              >规则及接入域名各最多 {{ MAX_ROUTING_ITEMS }} 项。优先级为 0..1_000_000 的站点内唯一整数，数值越大越优先；路径段前缀 /api 匹配 /api 和
              /api/users，不匹配 /apix。未命中规则时使用默认目标。路径 percent 编码发送时统一为大写。</p
            >
            <a-table :columns="ruleColumns" :data-source="input.rules" row-key="id" :pagination="false" :scroll="{ x: 1080 }" size="small">
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'id'"
                  ><span class="routing-id">{{ record.id }}</span></template
                >
                <template v-else-if="column.key === 'domain'">
                  <a-select
                    v-model:value="record.domainId"
                    :aria-label="`规则 ${index + 1} 域名`"
                    :options="ruleDomainOptions"
                    :disabled="!canWrite || locked"
                    allow-clear
                    placeholder="全部接入域名"
                    @change="record.domainId = record.domainId || null"
                  />
                </template>
                <template v-else-if="column.key === 'path'">
                  <a-input v-model:value="record.path" :aria-label="`规则 ${index + 1} 路径`" placeholder="/api" :disabled="!canWrite || locked" />
                </template>
                <template v-else-if="column.key === 'matchType'">
                  <a-select
                    v-model:value="record.matchType"
                    :aria-label="`规则 ${index + 1} 匹配方式`"
                    :options="matchOptions"
                    :disabled="!canWrite || locked"
                  />
                </template>
                <template v-else-if="column.key === 'priority'">
                  <a-input-number
                    v-model:value="record.priority"
                    :aria-label="`规则 ${index + 1} 优先级`"
                    :precision="0"
                    :min="0"
                    :max="MAX_ROUTING_PRIORITY"
                    :disabled="!canWrite || locked"
                  />
                </template>
                <template v-else-if="column.key === 'target'">
                  <a-select
                    v-model:value="record.targetId"
                    :aria-label="`规则 ${index + 1} 目标`"
                    :options="targetOptions"
                    :disabled="!canWrite || locked"
                    placeholder="选择目标"
                  />
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-button v-if="canWrite" type="link" danger size="small" :disabled="locked" @click="removeRule(record.id)">删除</a-button>
                </template>
              </template>
            </a-table>
            <a-space class="routing-actions" wrap>
              <a-button v-if="canWrite" :disabled="locked || input.rules.length >= MAX_ROUTING_ITEMS" @click="addRule">添加规则</a-button>
              <a-button v-if="canWrite" type="primary" :disabled="locked" :loading="busy === 'save'" @click="save">保存草稿</a-button>
              <a-button v-if="canWrite" :disabled="locked || dirty || state.draft?.status !== 'DRAFT'" @click="transition('submit')"
                >提交审批</a-button
              >
              <a-button
                v-if="canApprove"
                :disabled="locked || dirty || state.draft?.status !== 'SUBMITTED' || selfApprovalBlocked || missingApproverIdentity"
                @click="transition('approve')"
                >审批通过</a-button
              >
              <a-popconfirm
                v-if="canPublish"
                title="确认将已审批快照设为期望版本？仍需等待节点确认。"
                :disabled="!publishReady"
                @confirm="transition('publish')"
              >
                <a-button type="primary" :disabled="!publishReady">发布版本</a-button>
              </a-popconfirm>
            </a-space>
            <p class="routing-note"
              >先保存再提交；审批与发布只操作已保存且未被修改的版本。编辑需要站点管理员/运营及平台写权限，审批/发布需要站点管理员及对应平台权限。</p
            >
          </a-card>
          <a-card title="模拟请求（不发起网络连接）" size="small">
            <a-form layout="vertical" @submit.prevent="simulate">
              <a-row :gutter="16">
                <a-col :xs="24" :sm="12"
                  ><a-form-item label="访问域名"
                    ><a-input v-model:value="hostname" placeholder="www.example.com（不含协议和端口）" :disabled="locked" /></a-form-item
                ></a-col>
                <a-col :xs="24" :sm="12"
                  ><a-form-item label="请求路径"><a-input v-model:value="requestPath" placeholder="/api/users" :disabled="locked" /></a-form-item
                ></a-col>
              </a-row>
              <a-button html-type="submit" :disabled="locked" :loading="busy === 'simulate'">模拟当前表单</a-button>
            </a-form>
            <a-descriptions v-if="simulation" class="routing-actions" size="small" bordered :column="1">
              <a-descriptions-item label="命中规则">{{ simulation.ruleId || '默认目标（未命中规则）' }}</a-descriptions-item>
              <a-descriptions-item label="目标 ID">{{ simulation.targetId }}</a-descriptions-item>
              <a-descriptions-item label="目标地址"
                ><span class="routing-id">{{ simulation.url }}</span></a-descriptions-item
              >
            </a-descriptions>
            <p class="routing-note">模拟使用当前表单，可验证未保存的草稿；结果不代表目标可达、证书可用或节点已应用。</p>
          </a-card>
          <BasicTable
            :columns="releaseColumns"
            :data-source="state.releases"
            row-key="id"
            :pagination="{ pageSize: 10 }"
            :show-table-setting="false"
            :can-resize="false"
            :show-index-column="false"
            :scroll="{ x: 1200 }"
            title="发布历史（最近 50 条）"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'id'"
                ><span class="routing-id">{{ record.id }}</span></template
              >
              <template v-else-if="column.key === 'hash'"
                ><span class="routing-id">{{ record.hash }}</span></template
              >
              <template v-else-if="column.key === 'source'">{{ record.sourceReleaseId || '—' }}</template>
              <template v-else-if="column.key === 'ack'">
                <a-tag :color="record.desired ? (ackMatches(record) ? 'green' : 'orange') : 'default'">{{ releaseStatus(record) }}</a-tag>
                <div v-if="ackMatches(record)">确认时间：{{ record.ack.appliedAt }}</div>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-popconfirm
                  v-if="canPublish"
                  title="将此历史版本生成新草稿，覆盖当前草稿；之后仍需提交、审批、发布。确认继续？"
                  :disabled="locked"
                  @confirm="rollback(record.id)"
                >
                  <a-button type="link" size="small" :disabled="locked">生成回滚草稿</a-button>
                </a-popconfirm>
              </template>
            </template>
          </BasicTable>
          <p class="routing-note"
            >“当前已确认”仅表示当前期望版本收到匹配版本 ID 与摘要的 ACK；历史 ACK 不能证明当前节点健康。回滚不会立即切换流量。</p
          >
        </template>
      </a-spin>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { BasicForm, type FormSchema } from '/@/components/Form';
  import { BasicTable, type BasicColumn } from '/@/components/Table';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useUserStore } from '/@/store/modules/user';
  import { buildUUID } from '/@/utils/uuid';
  import { flagOn, listDomains, listTargets, type Domain, type Site, type Target } from '../resources/resources.api';
  import {
    approveRouting,
    getRouting,
    publishRouting,
    rollbackRouting,
    RoutingConflictError,
    saveDraft,
    simulateRouting,
    submitRouting,
    type DraftInput,
    type ReleaseView,
    type RoutingState,
    type SimulationResult,
  } from './routing.api';
  import {
    canonicalRoutingPath,
    MAX_ROUTING_ITEMS,
    MAX_ROUTING_PRIORITY,
    routingValidationError,
    validateRoutingDraft,
    validateRoutingPath,
  } from './routing.validation';

  const props = defineProps<{ site: Site }>();
  const userStore = useUserStore();
  const { hasPermission } = usePermission();
  const canRead = computed(() => hasPermission('siteplatform:read'));
  const canWrite = computed(() => canRead.value && ['ADMIN', 'EDITOR'].includes(props.site.myRole) && hasPermission('siteplatform:write'));
  const canApprove = computed(() => canRead.value && props.site.myRole === 'ADMIN' && hasPermission('siteplatform:approve'));
  const canPublish = computed(() => canRead.value && props.site.myRole === 'ADMIN' && hasPermission('siteplatform:publish'));
  const identity = computed(() =>
    JSON.stringify([
      userStore.getTenant,
      userStore.shareTenantId,
      userStore.getUserInfo.id,
      props.site.id,
      props.site.environment,
      props.site.myRole,
      canRead.value,
      canWrite.value,
      canApprove.value,
      canPublish.value,
    ])
  );
  const emptyInput = (): DraftInput => ({ revision: 0, defaultTargetId: '', domainIds: [], rules: [] });
  const state = ref<RoutingState>({ draft: null, releases: [] });
  const input = ref<DraftInput>(emptyInput());
  const baseline = ref('');
  const targets = ref<Target[]>([]);
  const domains = ref<Domain[]>([]);
  const ready = ref(false);
  const loading = ref(false);
  const busy = ref('');
  const needsRefresh = ref(false);
  const error = ref('');
  const notice = ref('');
  const hostname = ref('');
  const requestPath = ref('/');
  const simulation = ref<SimulationResult>();
  let disposed = false;
  let generation = 0;
  let loadVersion = 0;
  const controllers = new Set<AbortController>();
  const dirty = computed(() => ready.value && JSON.stringify(input.value) !== baseline.value);
  const locked = computed(() => !ready.value || loading.value || !!busy.value || needsRefresh.value);
  const selfApprovalBlocked = computed(
    () => props.site.environment === 'prod' && !!state.value.draft?.submittedBy && state.value.draft.submittedBy === userStore.getUserInfo.id
  );
  const missingApproverIdentity = computed(() => props.site.environment === 'prod' && (!userStore.getUserInfo.id || !state.value.draft?.submittedBy));
  const publishReady = computed(() => canPublish.value && !locked.value && !dirty.value && state.value.draft?.status === 'APPROVED');
  const targetOptions = computed(() =>
    targets.value.map((target) => ({
      value: target.id,
      label: `${target.name} · ${target.url}${flagOn(target.enabled) ? '' : '（已停用）'}`,
      disabled: !flagOn(target.enabled),
    }))
  );
  const ruleDomainOptions = computed(() =>
    domains.value
      .filter((domain) => input.value.domainIds.includes(domain.id))
      .map((domain) => ({ value: domain.id, label: domain.hostname, disabled: !flagOn(domain.verified) }))
  );
  const missingDomainIds = computed(() => input.value.domainIds.filter((id) => !domains.value.some((domain) => domain.id === id)));
  const statusLabels = { DRAFT: '草稿', SUBMITTED: '待审批', APPROVED: '已审批，待发布' };
  const matchOptions = [
    { label: '精确匹配', value: 'EXACT' },
    { label: '路径段前缀', value: 'PREFIX' },
  ];
  const draftSchemas: FormSchema[] = [
    { field: 'defaultTargetId', label: '默认目标', component: 'Select', slot: 'defaultTarget' },
    { field: 'domainIds', label: '接入域名', component: 'Select', slot: 'domains' },
  ];
  const ruleColumns = [
    { title: '规则 ID', key: 'id', width: 120 },
    { title: '域名（留空适用全部）', key: 'domain', width: 210 },
    { title: '路径', key: 'path', width: 160 },
    { title: '匹配方式', key: 'matchType', width: 140 },
    { title: '优先级', key: 'priority', width: 110 },
    { title: '目标地址', key: 'target', width: 250 },
    { title: '操作', key: 'action', width: 80 },
  ];
  const releaseColumns: BasicColumn[] = [
    { title: '版本 ID', key: 'id', width: 160 },
    { title: '摘要 SHA256', key: 'hash', width: 200 },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
    { title: '发布人 ID', dataIndex: 'createdBy', width: 160 },
    { title: '回滚来源版本', key: 'source', width: 160 },
    { title: '期望版本 / 节点确认', key: 'ack', width: 240 },
    { title: '操作', key: 'action', width: 150 },
  ];

  function snapshot() {
    const version = generation;
    const key = identity.value;
    return () => !disposed && version === generation && key === identity.value && canRead.value;
  }
  function invalidate() {
    generation++;
    loadVersion++;
    controllers.forEach((controller) => controller.abort());
    controllers.clear();
    state.value = { draft: null, releases: [] };
    input.value = emptyInput();
    baseline.value = '';
    targets.value = [];
    domains.value = [];
    ready.value = false;
    loading.value = false;
    busy.value = '';
    needsRefresh.value = false;
    error.value = '';
    notice.value = '';
    hostname.value = '';
    requestPath.value = '/';
    simulation.value = undefined;
  }
  function draftInput(): DraftInput {
    return {
      ...input.value,
      domainIds: [...input.value.domainIds],
      rules: input.value.rules.map((rule) => ({ ...rule, path: canonicalRoutingPath(rule.path), domainId: rule.domainId || null })),
    };
  }
  async function load() {
    if (!canRead.value || disposed) return;
    const valid = snapshot();
    const version = ++loadVersion;
    const current = () => valid() && version === loadVersion;
    const siteId = props.site.id;
    const controller = new AbortController();
    controllers.add(controller);
    loading.value = true;
    ready.value = false;
    error.value = '';
    simulation.value = undefined;
    try {
      const [routing, targetRows, domainRows] = await Promise.all([getRouting(siteId, controller.signal), listTargets(siteId), listDomains(siteId)]);
      if (!current()) return;
      state.value = routing;
      targets.value = targetRows;
      domains.value = domainRows;
      const draft = routing.draft;
      input.value = draft
        ? { ...draft.input, revision: draft.revision, domainIds: [...draft.input.domainIds], rules: draft.input.rules.map((rule) => ({ ...rule })) }
        : emptyInput();
      baseline.value = JSON.stringify(input.value);
      needsRefresh.value = false;
      ready.value = true;
    } catch {
      if (current()) error.value = '分流配置加载失败，请刷新重试，并确认租户成员和站点权限。';
    } finally {
      controllers.delete(controller);
      if (current()) loading.value = false;
    }
  }
  function validateInput() {
    return validateRoutingDraft(input.value, targets.value, domains.value);
  }
  function addRule() {
    if (!canWrite.value || locked.value) return;
    if (input.value.rules.length >= MAX_ROUTING_ITEMS) {
      error.value = `规则最多 ${MAX_ROUTING_ITEMS} 条，请删除多余规则后再添加。`;
      return;
    }
    const priorities = new Set(input.value.rules.map((rule) => rule.priority));
    let priority = 10;
    while (priorities.has(priority)) priority++;
    const id = `r${buildUUID().slice(0, 31)}`;
    input.value.rules.push({ id, domainId: null, path: '/', matchType: 'PREFIX', priority, targetId: input.value.defaultTargetId });
  }
  function removeRule(id: string) {
    if (canWrite.value && !locked.value) input.value.rules = input.value.rules.filter((rule) => rule.id !== id);
  }
  function removeMissingDomains() {
    if (canWrite.value && !locked.value) input.value.domainIds = input.value.domainIds.filter((id) => !missingDomainIds.value.includes(id));
  }
  async function run(action: string, permitted: boolean, request: (site: string, signal: AbortSignal) => Promise<unknown>, success: string) {
    if (!permitted || locked.value) return;
    const current = snapshot();
    const controller = new AbortController();
    controllers.add(controller);
    busy.value = action;
    error.value = '';
    notice.value = '';
    simulation.value = undefined;
    try {
      await request(props.site.id, controller.signal);
      if (!current()) return;
      notice.value = success;
      await load();
    } catch (cause) {
      if (!current()) return;
      const validation = routingValidationError(cause);
      needsRefresh.value = !validation;
      error.value =
        cause instanceof RoutingConflictError
          ? cause.message
          : validation
            ? `输入校验失败，可直接修改后重试。${validation.message}`
            : `操作失败，请刷新确认最新版本、资源与权限后重试。${cause instanceof Error ? ` ${cause.message}` : ''}`;
    } finally {
      controllers.delete(controller);
      if (current()) busy.value = '';
    }
  }
  async function save() {
    if (!canWrite.value || locked.value) return;
    error.value = validateInput();
    if (error.value) return;
    const data = draftInput();
    await run('save', canWrite.value, (site, signal) => saveDraft(site, data, signal), '草稿已保存，未切换流量；审批及候选内容已清空。');
  }
  async function transition(action: 'submit' | 'approve' | 'publish') {
    if (dirty.value || locked.value) return;
    const status = state.value.draft?.status;
    const revision = input.value.revision;
    if (action === 'submit' && status === 'DRAFT') {
      await run(action, canWrite.value, (site, signal) => submitRouting(site, revision, signal), '草稿已提交审批。');
    } else if (action === 'approve' && status === 'SUBMITTED') {
      await run(
        action,
        canApprove.value && !selfApprovalBlocked.value && !missingApproverIdentity.value,
        (site, signal) => approveRouting(site, revision, signal),
        '审批已通过，尚未发布。'
      );
    } else if (action === 'publish' && status === 'APPROVED') {
      await run(
        action,
        canPublish.value,
        (site, signal) => publishRouting(site, revision, signal),
        '期望版本已更新，请等待节点确认；本操作不会自动配置证书与节点。'
      );
    }
  }
  async function rollback(release: string) {
    const revision = input.value.revision;
    await run(
      'rollback',
      canPublish.value,
      (site, signal) => rollbackRouting(site, release, revision, signal),
      '历史版本已生成新草稿；请重新提交、审批、发布，当前流量未切换。'
    );
  }
  async function simulate() {
    if (locked.value || !canRead.value) return;
    error.value = validateInput();
    if (error.value) return;
    if (!hostname.value.trim() || /[/:?#\s]/.test(hostname.value.trim())) {
      error.value = '请输入不含协议、端口的访问域名。';
      return;
    }
    error.value = validateRoutingPath(requestPath.value);
    if (error.value) return;
    const current = snapshot();
    const controller = new AbortController();
    controllers.add(controller);
    busy.value = 'simulate';
    simulation.value = undefined;
    notice.value = '';
    try {
      const result = await simulateRouting(
        props.site.id,
        { hostname: hostname.value.trim(), path: canonicalRoutingPath(requestPath.value), input: draftInput() },
        controller.signal
      );
      if (current()) simulation.value = result;
    } catch (cause) {
      if (current()) {
        if (cause instanceof RoutingConflictError) needsRefresh.value = true;
        error.value =
          cause instanceof RoutingConflictError
            ? cause.message
            : `模拟失败，请检查域名、路径与草稿资源。${cause instanceof Error ? ` ${cause.message}` : ''}`;
      }
    } finally {
      controllers.delete(controller);
      if (current()) busy.value = '';
    }
  }
  function ackMatches(release: ReleaseView) {
    return !!release.ack && release.ack.releaseId === release.id && release.ack.hash === release.hash;
  }
  function releaseStatus(release: ReleaseView) {
    if (release.desired) return ackMatches(release) ? '当前已确认（非健康检测）' : '当前期望版本 · 待确认';
    return ackMatches(release) ? '历史版本 · 曾确认' : '历史版本 · 无匹配确认';
  }
  watch(
    [input, hostname, requestPath],
    () => {
      simulation.value = undefined;
    },
    { deep: true, flush: 'sync' }
  );
  watch(
    identity,
    () => {
      invalidate();
      if (canRead.value) void load();
    },
    { immediate: true, flush: 'sync' }
  );
  onBeforeUnmount(() => {
    disposed = true;
    invalidate();
  });
</script>

<style scoped lang="less">
  .routing-panel {
    display: flex;
    flex-direction: column;
    gap: 16px;
    :deep(.ant-card),
    :deep(.ant-descriptions),
    :deep(.ant-alert) {
      margin-bottom: 16px;
    }
    :deep(.ant-select),
    :deep(.ant-input-number) {
      width: 100%;
    }
  }
  .routing-actions {
    margin-top: 16px;
  }
  .routing-note {
    margin: 12px 0;
    color: @text-color-secondary;
  }
  .routing-id {
    overflow-wrap: anywhere;
    user-select: all;
    font-family: monospace;
  }
</style>
