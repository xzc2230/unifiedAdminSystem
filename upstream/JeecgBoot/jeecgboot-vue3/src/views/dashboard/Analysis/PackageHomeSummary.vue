<template>
  <div class="p-4 pb-0">
    <a-row :gutter="16">
      <a-col v-for="group in groups" :key="group.key" :xs="24" :md="12" class="home-count-col">
        <a-card :bordered="false" :loading="loading" :title="group.title">
          <a-row>
            <a-col v-for="item in group.items" :key="item.label" :span="8">
              <a-statistic :title="item.label" :value="item.value" />
            </a-col>
          </a-row>
        </a-card>
      </a-col>
    </a-row>
    <div class="home-count-note">{{ note }}</div>
  </div>
</template>
<script lang="ts" setup>
  import { computed, onMounted, ref } from 'vue';
  import { getPackageHomeSummary, PackageHomeCounts } from './api';

  const empty: PackageHomeCounts = { online: 0, review: 0, dropped: 0 };
  const loading = ref(true);
  const failed = ref(false);
  const android = ref<PackageHomeCounts>({ ...empty });
  const ios = ref<PackageHomeCounts>({ ...empty });

  const groups = computed(() => [
    { key: 'android', title: 'Android', items: metrics(android.value) },
    { key: 'ios', title: 'iOS', items: metrics(ios.value) },
  ]);
  const note = computed(() =>
    failed.value
      ? '统计暂时无法加载'
      : '在线包：已上架，且包统计里有对应商店链接。在线审核：待上架，预购不计入。掉包：已下架，且包统计里有掉包日期。同一包名或 Apple ID 只计一次。'
  );

  function metrics(counts: PackageHomeCounts) {
    return [
      { label: '在线包', value: counts.online },
      { label: '在线审核', value: counts.review },
      { label: '掉包', value: counts.dropped },
    ];
  }

  onMounted(async () => {
    try {
      const result = await getPackageHomeSummary();
      android.value = normalize(result?.android);
      ios.value = normalize(result?.ios);
    } catch {
      failed.value = true;
    } finally {
      loading.value = false;
    }
  });

  function normalize(counts?: PackageHomeCounts): PackageHomeCounts {
    return {
      online: Number(counts?.online) || 0,
      review: Number(counts?.review) || 0,
      dropped: Number(counts?.dropped) || 0,
    };
  }
</script>
<style lang="less" scoped>
  .home-count-col {
    margin-bottom: 16px;
  }
  .home-count-note {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    line-height: 20px;
  }
</style>
