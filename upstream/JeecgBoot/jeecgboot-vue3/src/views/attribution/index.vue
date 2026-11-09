<template>
  <div v-auth="'attribution:application:list'" class="attribution-page">
    <div class="page-heading">
      <h1>应用管理</h1>
      <a-button v-auth="'attribution:application:add'" type="primary" @click="openModal(true, {})">+ 新建应用</a-button>
    </div>
    <div class="search-bar">
      <a-input-search
        v-model:value="query"
        allow-clear
        placeholder="搜索应用名或渠道名"
        aria-label="搜索应用名或渠道名"
        enter-button="搜索"
        @search="search"
        @change="clearSearch"
      />
    </div>
    <a-alert v-if="listError" type="error" show-icon :message="listError" class="list-error">
      <template #action><a-button size="small" :loading="loading" @click="loadList">重试</a-button></template>
    </a-alert>
    <BasicTable
      v-show="!listError"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      :row-key="rowKey"
      :show-index-column="false"
      :show-table-setting="false"
      :use-search-form="false"
      :can-resize="false"
      :immediate="false"
      :striped="false"
      :scroll="{ x: 900 }"
      :expanded-row-keys="expandedKeys"
      :is-tree-table="true"
      :show-expand-column="false"
      :row-expandable="(record) => record.offerCount > 1"
      @change="changePage"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'fallbackUrl'"><UrlText :value="record.fallbackUrl" /></template>
        <template v-else-if="column.dataIndex === 'firstOfferUrl'">
          <span v-if="record.offerCount === 0">—</span>
          <div v-else class="offer-cell">
            <UrlText :value="record.firstOfferUrl || '—'" />
            <a-button
              v-if="record.offerCount > 1"
              type="link"
              size="small"
              :disabled="loading"
              :aria-expanded="expandedKeys.includes(rowKey(record))"
              @click="toggleOffers(record)"
            >
              {{ expandedKeys.includes(rowKey(record)) ? '收起' : `查看(${record.offerCount})` }}
            </a-button>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-if="record.editable" v-auth="'attribution:application:edit'" type="link" size="small" @click="openModal(true, { row: record })"
            >编辑</a-button
          >
          <span v-else class="readonly-label">只读</span>
        </template>
        <template v-else>{{ record[column.dataIndex] }}</template>
      </template>
      <template #expandedRowRender="{ record }">
        <div class="expanded-offers">
          <a-spin v-if="details[rowKey(record)]?.loading" tip="正在读取 Offer…" />
          <a-alert v-else-if="details[rowKey(record)]?.error" type="error" show-icon :message="details[rowKey(record)].error">
            <template #action>
              <a-button size="small" :disabled="loading" @click="details[rowKey(record)].conflict ? loadList() : loadOffers(record)">
                {{ details[rowKey(record)].conflict ? '刷新列表' : '重试' }}
              </a-button>
            </template>
          </a-alert>
          <ol v-else class="offer-details">
            <li v-for="(offer, index) in details[rowKey(record)]?.offers || []" :key="offer.bindingId || index">
              <span class="detail-number">Offer {{ index + 1 }}</span>
              <div class="detail-content">
                <UrlText :value="offer.targetUrl" />
                <p>{{ conditionSummary(offer) }}</p>
              </div>
              <span class="detail-weight"
                >权重 <strong>{{ offer.weight }}</strong
                ><small v-if="offer.weight === 0">不参与新点击分配</small></span
              >
            </li>
          </ol>
        </div>
      </template>
      <template #emptyText><a-empty :description="loading ? '正在读取应用…' : '未找到应用'" /></template>
    </BasicTable>
    <AttributionModal @register="registerModal" @success="loadList" />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
  import { BasicTable } from '/@/components/Table';
  import type { BasicColumn } from '/@/components/Table';
  import { useModal } from '/@/components/Modal';
  import { usePermission } from '/@/hooks/web/usePermission';
  import AttributionModal from './AttributionModal.vue';
  import UrlText from './UrlText.vue';
  import { getOffers, listApplications } from './attribution.api';
  import { conditionSummary, readError } from './attribution.form.mjs';
  import type { ApplicationRow, AttributionOffer } from './attribution.types';

  defineOptions({ name: 'AttributionIndex' });

  const columns: BasicColumn[] = [
    { title: '应用名', dataIndex: 'applicationName', width: 160, align: 'left', ellipsis: true },
    { title: '渠道名', dataIndex: 'channelName', width: 140, align: 'left', ellipsis: true },
    { title: '兜底地址', dataIndex: 'fallbackUrl', width: 260, align: 'left' },
    { title: 'Offer 地址', dataIndex: 'firstOfferUrl', width: 300, align: 'left' },
    { title: '操作', dataIndex: 'action', width: 80, align: 'center' },
  ];
  const [registerModal, { openModal }] = useModal();
  const { hasPermission } = usePermission();
  const rows = ref<ApplicationRow[]>([]);
  const query = ref('');
  const activeQuery = ref('');
  const pageNo = ref(1);
  const pageSize = ref(20);
  const total = ref(0);
  const loading = ref(false);
  const listError = ref('');
  const expandedKeys = ref<string[]>([]);
  const details = ref<Record<string, { loading: boolean; error: string; conflict: boolean; offers: AttributionOffer[] }>>({});
  const rowKey = (record: ApplicationRow) => JSON.stringify([record.applicationId, record.channelId]);
  const pagination = computed(() => ({
    current: pageNo.value,
    pageSize: pageSize.value,
    total: total.value,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showTotal: (count: number) => `共 ${count} 个应用`,
  }));
  let requestId = 0;
  let listGeneration = 0;

  async function loadList() {
    if (!hasPermission('attribution:application:list')) return;
    const current = ++requestId;
    listGeneration++;
    expandedKeys.value = [];
    details.value = {};
    loading.value = true;
    listError.value = '';
    try {
      const data = await listApplications({ q: activeQuery.value, pageNo: pageNo.value, pageSize: pageSize.value });
      if (current !== requestId) return;
      if (!Array.isArray(data.records) || !Number.isFinite(data.total)) throw new Error('应用列表读取不完整，请重试');
      if (pageNo.value > 1 && data.records.length === 0 && data.total > 0) {
        pageNo.value = Math.max(1, Math.ceil(data.total / pageSize.value));
        void loadList();
        return;
      }
      rows.value = data.records;
      total.value = data.total;
    } catch (error) {
      if (current === requestId) listError.value = readError(error).message;
    } finally {
      if (current === requestId) loading.value = false;
    }
  }
  function search() {
    activeQuery.value = query.value.trim();
    pageNo.value = 1;
    void loadList();
  }
  function clearSearch() {
    if (!query.value && activeQuery.value) search();
  }
  function changePage(page: { current?: number; pageSize?: number }) {
    pageNo.value = page.pageSize !== pageSize.value ? 1 : page.current || 1;
    pageSize.value = page.pageSize || 20;
    void loadList();
  }
  function toggleOffers(record: ApplicationRow) {
    if (loading.value || record.offerCount <= 1 || !isCurrentRow(record)) return;
    const key = rowKey(record);
    if (expandedKeys.value.includes(key)) expandedKeys.value = expandedKeys.value.filter((value) => value !== key);
    else {
      expandedKeys.value = [...expandedKeys.value, key];
      if (!details.value[key]) void loadOffers(record);
    }
  }
  function isCurrentRow(record: ApplicationRow) {
    return rows.value.some((current) => rowKey(current) === rowKey(record) && current.configVersion === record.configVersion);
  }
  async function loadOffers(record: ApplicationRow) {
    const key = rowKey(record);
    if (loading.value || record.offerCount <= 1 || !isCurrentRow(record) || details.value[key]?.loading) return;
    const generation = listGeneration;
    const state = { loading: true, error: '', conflict: false, offers: [] as AttributionOffer[] };
    details.value[key] = state;
    const canCommit = () => generation === listGeneration && !loading.value && isCurrentRow(record) && details.value[key] === stateProxy;
    const stateProxy = details.value[key];
    try {
      const data = await getOffers(record);
      if (!canCommit()) return;
      if (data.configVersion !== record.configVersion) throw { code: 409, message: '应用配置已更新，请刷新列表后查看' };
      if (!Array.isArray(data.offers) || data.offers.length !== record.offerCount) throw new Error('Offer 数据读取不完整，请重试');
      details.value[key].offers = [...data.offers].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    } catch (error) {
      if (!canCommit()) return;
      const failure = readError(error);
      details.value[key].error = failure.message;
      details.value[key].conflict = failure.conflict;
    } finally {
      if (canCommit()) details.value[key].loading = false;
      else if (details.value[key] === stateProxy) {
        delete details.value[key];
        expandedKeys.value = expandedKeys.value.filter((value) => value !== key);
      }
    }
  }
  onMounted(loadList);
  onBeforeUnmount(() => {
    requestId++;
    listGeneration++;
  });
</script>

<style scoped lang="less">
  .attribution-page {
    padding: 24px;
    background: #fff;
    margin: 16px;
    border-radius: 8px;
  }
  .page-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;
  }
  .page-heading h1 {
    margin: 0;
    font-size: 20px;
    line-height: 1.4;
    font-weight: 600;
  }
  .search-bar {
    max-width: 440px;
    margin-bottom: 20px;
  }
  .list-error {
    margin-bottom: 16px;
  }
  .offer-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .offer-cell > :first-child {
    flex: 1;
    min-width: 0;
  }
  .offer-cell :deep(.ant-btn) {
    flex-shrink: 0;
    padding-right: 0;
  }
  .readonly-label {
    color: #66758a;
    font-size: 12px;
  }
  .expanded-offers {
    padding: 12px 20px;
  }
  .offer-details {
    list-style: none;
    margin: 0;
    padding: 0;
  }
  .offer-details li {
    display: grid;
    grid-template-columns: 70px minmax(0, 1fr) 150px;
    gap: 16px;
    padding: 14px 0;
    border-bottom: 1px solid #e7edf5;
  }
  .offer-details li:last-child {
    border-bottom: 0;
  }
  .detail-number {
    color: #526174;
    font-size: 12px;
    font-weight: 600;
  }
  .detail-content {
    min-width: 0;
  }
  .detail-content p {
    margin: 6px 0 0;
    color: #66758a;
    font-size: 12px;
    overflow-wrap: anywhere;
  }
  .detail-weight {
    text-align: right;
    font-size: 12px;
    color: #66758a;
    font-variant-numeric: tabular-nums;
  }
  .detail-weight strong {
    margin-left: 8px;
    font-size: 14px;
    color: #26364a;
  }
  .detail-weight small {
    display: block;
    margin-top: 6px;
  }
  @media (max-width: 600px) {
    .attribution-page {
      margin: 8px;
      padding: 16px;
    }
    .search-bar {
      max-width: none;
    }
    .expanded-offers {
      padding: 8px;
    }
  }
</style>
