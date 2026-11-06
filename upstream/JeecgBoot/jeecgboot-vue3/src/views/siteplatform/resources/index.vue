<template>
  <div class="site-resources">
    <a-alert
      type="info"
      show-icon
      message="登记站点与域名后，可在分流与发布中配置草稿、审批及期望版本；实际接入需另行配置 DNS、证书与节点。"
      class="page-hint"
    />
    <a-alert v-if="!canRead" type="warning" show-icon message="暂无站点资源读取权限" />
    <template v-else>
      <a-alert v-if="listError" type="error" show-icon :message="listError" class="page-hint" />
      <BasicTable @register="registerTable" :dataSource="sites" :loading="listLoading">
        <template #tableTitle>
          <a-space>
            <a-button v-if="hasPermission('siteplatform:create')" type="primary" @click="edit('site')">登记站点</a-button>
            <a-button :loading="listLoading" @click="loadSites">刷新</a-button>
          </a-space>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="flagOn(record.enabled) ? 'green' : 'default'">{{ flagOn(record.enabled) ? '登记启用' : '登记停用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'myRole'">{{ roleLabels[record.myRole] || record.myRole }}</template>
          <template v-else-if="column.key === 'running'">
            <a-button type="link" size="small" @click="openSite(record, 'routing')">查看分流与发布</a-button>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openSite(record)">资源详情</a-button>
              <a-button v-if="canWriteSite(record)" type="link" size="small" @click="edit('site', record)">编辑</a-button>
            </a-space>
          </template>
        </template>
      </BasicTable>
      <div class="site-pagination">
        <a-pagination
          v-model:current="pageNo"
          v-model:pageSize="pageSize"
          :total="total"
          :show-total="(count) => `共 ${count} 个站点`"
          show-size-changer
          :page-size-options="['10', '20', '50', '100']"
          @change="loadSites"
        />
      </div>
    </template>

    <a-drawer
      :key="contextKey"
      :open="!!selected"
      :title="selected ? `${selected.name} · 站点资源` : '站点资源'"
      width="min(1100px, 96vw)"
      @close="closeSite"
    >
      <template v-if="selected">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="环境">{{ selected.environment }}</a-descriptions-item>
          <a-descriptions-item label="我的角色">{{ roleLabels[selected.myRole] }}</a-descriptions-item>
          <a-descriptions-item label="登记状态">{{ flagOn(selected.enabled) ? '启用' : '停用' }}</a-descriptions-item>
          <a-descriptions-item label="运行状态">查看分流与发布；登记状态不代表流量生效</a-descriptions-item>
        </a-descriptions>
        <p class="resource-note">资源变更仅保存登记信息，不切换公网流量。域名验证仅证明本次 DNS 控制权，不签发证书。</p>
        <a-tabs v-model:activeKey="activeTab" @change="loadTab">
          <a-tab-pane key="targets" tab="目标地址" />
          <a-tab-pane key="domains" tab="域名与验证" />
          <a-tab-pane key="routing" tab="分流与发布" />
          <a-tab-pane v-if="canManage" key="members" tab="站点成员" />
          <a-tab-pane key="audit" tab="操作记录（最近 100 条）" />
        </a-tabs>
        <RoutingPanel v-if="activeTab === 'routing'" :key="`${contextKey}:${tenantKey}:${selected.id}`" :site="selected" />
        <template v-else>
          <a-space class="resource-toolbar">
            <a-button v-if="activeTab === 'targets' && canWrite" type="primary" :disabled="busy" @click="edit('target', selected)"
              >登记目标地址</a-button
            >
            <a-button v-if="activeTab === 'domains' && canWrite" type="primary" :disabled="busy" @click="edit('domain', selected)">绑定域名</a-button>
            <a-button v-if="activeTab === 'members' && canManage" type="primary" :disabled="busy" @click="edit('member', selected)"
              >添加成员</a-button
            >
            <a-button :loading="tabLoading" :disabled="busy" @click="loadTab">刷新</a-button>
          </a-space>
          <a-alert v-if="detailError" type="error" show-icon :message="detailError" class="page-hint" />
          <a-alert v-if="actionError" type="error" show-icon :message="actionError" class="page-hint" />
          <a-spin :spinning="busy">
            <a-table
              v-if="activeTab === 'targets'"
              :columns="targetColumns"
              :data-source="targets"
              :loading="tabLoading"
              row-key="id"
              :pagination="false"
              :scroll="{ x: 700 }"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'enabled'">
                  <a-tag :color="flagOn(record.enabled) ? 'green' : 'default'">{{ flagOn(record.enabled) ? '登记启用' : '登记停用' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'action' && canWrite">
                  <a-space>
                    <a-button type="link" size="small" :disabled="busy" @click="edit('target', selected, record)">编辑</a-button>
                    <a-popconfirm title="确认移除此目标地址登记？" @confirm="remove('target', record.id)">
                      <a-button type="link" danger size="small" :disabled="busy">移除</a-button>
                    </a-popconfirm>
                  </a-space>
                </template>
              </template>
            </a-table>
            <a-table
              v-else-if="activeTab === 'domains'"
              :columns="domainColumns"
              :data-source="domains"
              :loading="tabLoading"
              row-key="id"
              :pagination="false"
              :scroll="{ x: 900 }"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'verified'">
                  <a-tag :color="flagOn(record.verified) ? 'green' : 'orange'">{{ flagOn(record.verified) ? '已验证' : '待验证' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'verifiedAt'">{{ record.verifiedAt || '—' }}</template>
                <template v-else-if="column.key === 'challengeExpires'">{{
                  flagOn(record.verified) ? '—' : record.challengeExpires || '—'
                }}</template>
                <template v-else-if="column.key === 'action' && canWrite">
                  <a-space wrap>
                    <template v-if="!flagOn(record.verified)">
                      <a-button type="link" size="small" :disabled="busy" @click="showChallenge(record)">DNS TXT</a-button>
                      <a-button type="link" size="small" :disabled="busy" @click="verify(record)">验证</a-button>
                    </template>
                    <a-popconfirm title="确认释放域名？释放后该域名可被其他站点绑定。" @confirm="remove('domain', record.id)">
                      <a-button type="link" danger size="small" :disabled="busy">解除绑定</a-button>
                    </a-popconfirm>
                  </a-space>
                </template>
              </template>
            </a-table>
            <a-table
              v-else-if="activeTab === 'members' && canManage"
              :columns="memberColumns"
              :data-source="members"
              :loading="tabLoading"
              row-key="userId"
              :pagination="false"
              :scroll="{ x: 700 }"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'role'">{{ roleLabels[record.role] }}</template>
                <template v-else-if="column.key === 'action'">
                  <span v-if="record.userId === userStore.getUserInfo.id">本人（保留管理员权限）</span>
                  <a-space v-else>
                    <a-button type="link" size="small" :disabled="busy" @click="edit('member', selected, record)">调整角色</a-button>
                    <a-popconfirm title="确认撤销该成员的站点访问权限？" @confirm="remove('member', record.userId)">
                      <a-button type="link" danger size="small" :disabled="busy">移除</a-button>
                    </a-popconfirm>
                  </a-space>
                </template>
              </template>
            </a-table>
            <a-table
              v-else-if="activeTab === 'audit'"
              :columns="auditColumns"
              :data-source="audit"
              :loading="tabLoading"
              row-key="id"
              :pagination="{ pageSize: 20 }"
              :scroll="{ x: 1000 }"
              size="small"
            />
          </a-spin>
        </template>
      </template>
    </a-drawer>

    <a-modal :open="challengeOpen && canWrite" title="DNS TXT 验证" :width="640" :footer="null" :mask-closable="false" @cancel="closeChallenge">
      <template v-if="canWrite">
        <a-alert
          type="info"
          show-icon
          message="在域名 DNS 中添加以下 TXT 记录，再执行验证。挑战有效期 30 分钟；DNS 生效后可重试。"
          class="page-hint"
        />
        <a-alert v-if="challengeError" type="error" show-icon :message="challengeError" class="page-hint" />
        <a-spin :spinning="challengeLoading || busy">
          <a-descriptions v-if="challenge" bordered :column="1" size="small">
            <a-descriptions-item label="记录名称"
              ><span class="dns-value">{{ challenge.name }}</span></a-descriptions-item
            >
            <a-descriptions-item label="类型">TXT</a-descriptions-item>
            <a-descriptions-item label="记录值"
              ><span class="dns-value">{{ challenge.value }}</span></a-descriptions-item
            >
            <a-descriptions-item label="到期时间">{{ challenge.expiresAt }}</a-descriptions-item>
          </a-descriptions>
          <a-space class="challenge-actions" wrap>
            <a-button :disabled="busy || challengeLoading" @click="fetchChallenge">重新读取</a-button>
            <a-button :disabled="busy || challengeLoading" @click="renew">重新生成挑战</a-button>
            <a-button type="primary" :disabled="busy || challengeLoading || !challenge" @click="verifyChallenge">验证域名</a-button>
          </a-space>
        </a-spin>
      </template>
    </a-modal>
    <ResourceEditor :context-key="contextKey" @register="registerEditor" @saved="editorSaved" />
  </div>
</template>

<script setup lang="ts">
  defineOptions({ name: 'SitePlatformResources' });
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { BasicTable, useTable, type BasicColumn } from '/@/components/Table';
  import { useModal } from '/@/components/Modal';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { useUserStore } from '/@/store/modules/user';
  import ResourceEditor from './ResourceEditor.vue';
  import RoutingPanel from '../routing/RoutingPanel.vue';
  import {
    flagOn,
    listSites,
    listTargets,
    listDomains,
    listMembers,
    listAudit,
    getChallenge,
    renewChallenge,
    verifyDomain,
    removeTarget,
    releaseDomain,
    removeMember,
    type Site,
    type Target,
    type Domain,
    type Member,
    type AuditRecord,
    type DomainChallenge,
    type EditorContext,
    type SiteRole,
  } from './resources.api';

  type Tab = 'targets' | 'domains' | 'routing' | 'members' | 'audit';
  const userStore = useUserStore();
  const tenantKey = computed(() => JSON.stringify([userStore.getTenant, userStore.shareTenantId]));
  const { hasPermission } = usePermission();
  const { createMessage } = useMessage();
  const canRead = computed(() => hasPermission('siteplatform:read'));
  const canWriteSite = (site: Site) => ['ADMIN', 'EDITOR'].includes(site.myRole) && hasPermission('siteplatform:write');
  const selected = ref<Site>();
  const canWrite = computed(() => canRead.value && !!selected.value && canWriteSite(selected.value));
  const canManage = computed(() => canRead.value && selected.value?.myRole === 'ADMIN' && hasPermission('siteplatform:manage'));
  const roleLabels: Record<SiteRole, string> = { ADMIN: '管理员', EDITOR: '运营', VIEWER: '只读' };
  const sites = ref<Site[]>([]);
  const total = ref(0);
  const pageNo = ref(1);
  const pageSize = ref(20);
  const listLoading = ref(false);
  const listError = ref('');
  const targets = ref<Target[]>([]);
  const domains = ref<Domain[]>([]);
  const members = ref<Member[]>([]);
  const audit = ref<AuditRecord[]>([]);
  const activeTab = ref<Tab>('targets');
  const tabLoading = ref(false);
  const busy = ref(false);
  const detailError = ref('');
  const actionError = ref('');
  const contextKey = ref(0);
  const challenge = ref<DomainChallenge>();
  const challengeDomain = ref<Domain>();
  const challengeOpen = ref(false);
  const challengeLoading = ref(false);
  const challengeError = ref('');
  let listVersion = 0;
  let tabVersion = 0;
  let challengeVersion = 0;
  let disposed = false;
  const [registerEditor, { openModal }] = useModal();
  const siteColumns: BasicColumn[] = [
    { title: '站点名称', dataIndex: 'name', width: 200 },
    { title: '环境', dataIndex: 'environment', width: 100 },
    { title: '登记状态', dataIndex: 'enabled', key: 'enabled', width: 130 },
    { title: '运行状态', key: 'running', width: 160 },
    { title: '我的角色', dataIndex: 'myRole', key: 'myRole', width: 100 },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
    { title: '操作', key: 'action', width: 170 },
  ];
  const [registerTable] = useTable({
    title: '站点资源',
    columns: siteColumns,
    rowKey: 'id',
    pagination: false,
    useSearchForm: false,
    showTableSetting: false,
    canResize: false,
    showIndexColumn: false,
  });
  const targetColumns = computed(() => [
    { title: '名称', dataIndex: 'name', width: 150 },
    { title: '目标地址', dataIndex: 'url', width: 300 },
    { title: '登记状态', key: 'enabled', width: 120 },
    ...(canWrite.value ? [{ title: '操作', key: 'action', width: 150 }] : []),
  ]);
  const domainColumns = computed(() => [
    { title: '域名', dataIndex: 'hostname', width: 180 },
    { title: '验证状态', key: 'verified', width: 100 },
    { title: '验证时间', key: 'verifiedAt', width: 180 },
    { title: '挑战到期时间', key: 'challengeExpires', width: 180 },
    ...(canWrite.value ? [{ title: '操作', key: 'action', width: 260 }] : []),
  ]);
  const memberColumns = [
    { title: '用户 ID', dataIndex: 'userId', width: 240 },
    { title: '用户名', dataIndex: 'username', width: 130 },
    { title: '姓名', dataIndex: 'realname', width: 110 },
    { title: '角色', key: 'role', width: 100 },
    { title: '操作', key: 'action', width: 210 },
  ];
  const auditColumns = [
    { title: '时间', dataIndex: 'createdAt', width: 180 },
    { title: '操作人 ID', dataIndex: 'actorId', width: 240 },
    { title: '动作', dataIndex: 'action', width: 160 },
    { title: '资源 ID', dataIndex: 'resourceId', width: 240 },
    { title: '摘要', dataIndex: 'summary', width: 300 },
  ];

  // Each async continuation must still belong to the same tenant and drawer session.
  function snapshot() {
    const key = contextKey.value;
    const tenant = tenantKey.value;
    const site = selected.value?.id;
    return () => !disposed && contextKey.value === key && tenant === tenantKey.value && selected.value?.id === site;
  }
  function closeChallenge() {
    challengeVersion++;
    challengeOpen.value = false;
    challenge.value = undefined;
    challengeDomain.value = undefined;
    challengeError.value = '';
    challengeLoading.value = false;
  }
  function closeSite() {
    contextKey.value++;
    tabVersion++;
    selected.value = undefined;
    targets.value = [];
    domains.value = [];
    members.value = [];
    audit.value = [];
    detailError.value = '';
    actionError.value = '';
    tabLoading.value = false;
    busy.value = false;
    closeChallenge();
  }
  function openSite(site: Site, tab: Tab = 'targets') {
    closeSite();
    selected.value = { ...site };
    activeTab.value = tab;
    void loadTab();
  }
  async function loadSites() {
    const version = ++listVersion;
    const tenant = tenantKey.value;
    if (!canRead.value || disposed) return;
    listLoading.value = true;
    listError.value = '';
    const current = () => !disposed && version === listVersion && tenant === tenantKey.value && canRead.value;
    try {
      const page = await listSites({ pageNo: pageNo.value, pageSize: pageSize.value });
      if (!current()) return;
      sites.value = page.records;
      total.value = page.total;
      // Refresh selected metadata only if this page still includes the selected site.
      const latest = page.records.find((site) => site.id === selected.value?.id);
      if (latest && latest.myRole !== selected.value?.myRole) closeSite();
      else if (latest) selected.value = { ...latest };
    } catch {
      if (current()) {
        sites.value = [];
        total.value = 0;
        listError.value = '站点加载失败，请刷新重试。';
      }
    } finally {
      if (current()) listLoading.value = false;
    }
  }
  async function loadTab() {
    const site = selected.value;
    const tab = activeTab.value;
    if (tab === 'routing') {
      tabVersion++;
      tabLoading.value = false;
      return;
    }
    if (!site || !canRead.value || (tab === 'members' && !canManage.value)) return;
    const valid = snapshot();
    const version = ++tabVersion;
    const current = () => valid() && version === tabVersion && tab === activeTab.value;
    tabLoading.value = true;
    detailError.value = '';
    targets.value = [];
    domains.value = [];
    members.value = [];
    audit.value = [];
    try {
      if (tab === 'targets') {
        const rows = await listTargets(site.id);
        if (current()) targets.value = rows;
      } else if (tab === 'domains') {
        const rows = await listDomains(site.id);
        if (current()) domains.value = rows;
      } else if (tab === 'members') {
        const rows = await listMembers(site.id);
        if (current() && canManage.value) members.value = rows;
      } else {
        const rows = await listAudit(site.id);
        if (current()) audit.value = rows;
      }
    } catch {
      if (current()) detailError.value = '资源加载失败，请刷新重试；成员或权限可能已变更。';
    } finally {
      if (current()) tabLoading.value = false;
    }
  }
  function edit(kind: EditorContext['kind'], site?: Site, record?: Target | Member) {
    if (
      kind === 'site' ? (site ? !canWriteSite(site) : !hasPermission('siteplatform:create')) : kind === 'member' ? !canManage.value : !canWrite.value
    )
      return;
    openModal(true, { kind, site: site ? { ...site } : undefined, record, contextKey: contextKey.value } satisfies EditorContext);
  }
  function editorSaved(data: EditorContext) {
    if (data.contextKey !== contextKey.value) return;
    createMessage.success('登记信息已保存');
    if (data.kind === 'site') void loadSites();
    else void loadTab();
  }
  async function runAction(request: (siteId: string) => Promise<unknown>, success: string, challengeAction = false) {
    const site = selected.value;
    if (!site || busy.value) return false;
    const current = snapshot();
    busy.value = true;
    actionError.value = '';
    challengeError.value = '';
    try {
      await request(site.id);
      if (!current()) return false;
      createMessage.success(success);
      await loadTab();
      return current();
    } catch {
      if (current()) {
        const message = challengeAction
          ? '验证操作失败：请检查 TXT 记录、DNS 生效情况和挑战有效期后重试。'
          : '操作失败，请刷新资源并检查权限后重试。';
        if (challengeAction && challengeOpen.value) challengeError.value = message;
        else actionError.value = message;
      }
      return false;
    } finally {
      if (current()) busy.value = false;
    }
  }
  async function remove(kind: 'target' | 'domain' | 'member', id: string) {
    if (kind === 'member' ? !canManage.value || id === userStore.getUserInfo.id : !canWrite.value) return;
    if (kind === 'target') await runAction((site) => removeTarget(site, id), '目标地址登记已移除');
    else if (kind === 'domain') {
      const removed = await runAction((site) => releaseDomain(site, id), '域名绑定已解除');
      if (removed && challengeDomain.value?.id === id) closeChallenge();
    } else await runAction((site) => removeMember(site, id), '成员权限已撤销');
  }
  async function showChallenge(domain: Domain) {
    if (!canWrite.value || busy.value) return;
    closeChallenge();
    challengeDomain.value = domain;
    challengeOpen.value = true;
    await fetchChallenge();
  }
  async function fetchChallenge() {
    const site = selected.value;
    const domain = challengeDomain.value;
    if (!site || !domain || !canWrite.value) return;
    const valid = snapshot();
    const version = ++challengeVersion;
    const current = () => valid() && version === challengeVersion && challengeOpen.value && canWrite.value;
    challengeLoading.value = true;
    challenge.value = undefined;
    challengeError.value = '';
    try {
      const result = await getChallenge(site.id, domain.id);
      if (current()) challenge.value = result;
    } catch {
      if (current()) challengeError.value = '无法读取挑战，挑战可能已过期或域名已验证。请刷新或重新生成。';
    } finally {
      if (current()) challengeLoading.value = false;
    }
  }
  async function renew() {
    const domain = challengeDomain.value;
    if (!domain || !canWrite.value) return;
    const valid = snapshot();
    const version = ++challengeVersion;
    challenge.value = undefined;
    const renewed = await runAction((site) => renewChallenge(site, domain.id), '挑战已重新生成，请更新 DNS TXT 记录', true);
    if (renewed && valid() && version === challengeVersion && challengeOpen.value) await fetchChallenge();
  }
  async function verify(domain: Domain) {
    if (!canWrite.value) return;
    const verified = await runAction((site) => verifyDomain(site, domain.id), '域名已验证，公网流量未切换', true);
    if (verified && challengeDomain.value?.id === domain.id) closeChallenge();
  }
  async function verifyChallenge() {
    if (challengeDomain.value) await verify(challengeDomain.value);
  }
  watch(canManage, (allowed) => {
    if (!allowed) {
      members.value = [];
      if (activeTab.value === 'members') {
        activeTab.value = 'targets';
        void loadTab();
      }
    }
  });
  watch(canWrite, (allowed) => {
    if (!allowed) closeChallenge();
  });
  watch(
    [tenantKey, canRead],
    () => {
      listVersion++;
      closeSite();
      sites.value = [];
      total.value = 0;
      pageNo.value = 1;
      listError.value = '';
      listLoading.value = false;
      if (canRead.value) void loadSites();
    },
    { immediate: true, flush: 'sync' }
  );
  onBeforeUnmount(() => {
    disposed = true;
    listVersion++;
    closeSite();
  });
</script>

<style scoped lang="less">
  .site-resources {
    padding: 16px;
  }
  .page-hint {
    margin-bottom: 16px;
  }
  .site-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
  .resource-note {
    margin: 16px 0 0;
    color: @text-color-secondary;
  }
  .resource-toolbar {
    margin-bottom: 16px;
  }
  .dns-value {
    overflow-wrap: anywhere;
    user-select: all;
    font-family: monospace;
  }
  .challenge-actions {
    margin-top: 20px;
  }
</style>
