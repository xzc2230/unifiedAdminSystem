<template>
  <section class="offer-conditions" aria-label="本条 Offer 条件">
    <div class="condition-heading">
      <span>本条条件关系</span>
      <a-radio-group :value="offer.matchMode" :disabled="disabled" size="small" @change="changeMode($event.target.value)">
        <a-radio-button value="AND">全部满足（AND）</a-radio-button>
        <a-radio-button value="OR">任一满足（OR）</a-radio-button>
      </a-radio-group>
    </div>
    <p v-if="!offer.conditions.length" class="condition-note">不限条件，所有符合投递要求的新点击均可参与。</p>
    <div v-for="(condition, index) in offer.conditions" :key="index" class="condition-entry">
      <div class="condition-row">
        <a-select
          :value="condition.field"
          :disabled="disabled || locked(condition)"
          aria-label="条件字段"
          :options="fieldOptions(condition)"
          @change="changeField(index, $event)"
        />
        <a-select
          :value="condition.operator"
          :disabled="disabled || locked(condition)"
          aria-label="条件操作符"
          :options="[
            { label: '是', value: 'IS' },
            { label: '不是', value: 'IS_NOT' },
          ]"
          @change="patch(index, { operator: $event })"
        />
        <a-select
          :value="condition.values"
          :disabled="disabled || locked(condition)"
          :mode="condition.field === 'COUNTRY' ? 'tags' : 'multiple'"
          :options="condition.field === 'OS' ? osOptions : []"
          :token-separators="[',', ' ']"
          :placeholder="condition.field === 'COUNTRY' ? 'ISO 两字母代码，如 CN、US' : '选择操作系统'"
          aria-label="条件值"
          @change="patch(index, { values: condition.field === 'COUNTRY' ? $event.map((value) => value.trim().toUpperCase()) : $event })"
        />
        <a-button type="text" danger :disabled="disabled" :aria-label="`移除第 ${index + 1} 条条件`" @click="remove(index)">移除</a-button>
      </div>
      <p v-if="locked(condition)" class="condition-note">国家/地区条件尚未开放；此条已有条件可保留或移除。</p>
      <p v-for="error in conditionErrors(index)" :key="error.path" class="field-error" role="alert">{{ error.message }}</p>
    </div>
    <p v-for="error in errors.filter((item) => item.path === `${path}.conditions`)" :key="error.path" class="field-error" role="alert">{{
      error.message
    }}</p>
    <a-button type="dashed" size="small" :disabled="disabled" @click="add">+ 添加条件</a-button>
    <p v-if="!countryEnabled" class="condition-note">国家/地区条件暂未开放。</p>
  </section>
</template>

<script setup lang="ts">
  import type { AttributionOffer, FieldError, OfferCondition } from './attribution.types';
  import { osOptions } from './attribution.form.mjs';

  const props = defineProps<{ offer: AttributionOffer; countryEnabled: boolean; disabled?: boolean; errors: FieldError[]; path: string }>();
  const emit = defineEmits<{ (event: 'update:offer', value: AttributionOffer): void }>();
  const locked = (condition: OfferCondition) => condition.field === 'COUNTRY' && !props.countryEnabled;
  const fieldOptions = (condition: OfferCondition) => [
    { label: '操作系统', value: 'OS' },
    ...(props.countryEnabled || condition.field === 'COUNTRY' ? [{ label: '国家/地区', value: 'COUNTRY', disabled: !props.countryEnabled }] : []),
  ];
  const conditionErrors = (index: number) => props.errors.filter((error) => error.path.startsWith(`${props.path}.conditions[${index}]`));
  function changeMode(matchMode: AttributionOffer['matchMode']) {
    if (props.disabled) return;
    emit('update:offer', { ...props.offer, matchMode });
  }
  function patch(index: number, value: Partial<OfferCondition>) {
    if (props.disabled) return;
    const conditions = props.offer.conditions.map((condition, position) => (position === index ? { ...condition, ...value } : condition));
    emit('update:offer', { ...props.offer, conditions });
  }
  function changeField(index: number, field: OfferCondition['field']) {
    patch(index, { field, values: [] });
  }
  function add() {
    if (props.disabled) return;
    emit('update:offer', { ...props.offer, conditions: [...props.offer.conditions, { field: 'OS', operator: 'IS', values: [] }] });
  }
  function remove(index: number) {
    if (props.disabled) return;
    emit('update:offer', { ...props.offer, conditions: props.offer.conditions.filter((_, position) => position !== index) });
  }
</script>

<style scoped lang="less">
  .offer-conditions {
    margin-top: 12px;
    padding: 16px;
    background: var(--attribution-condition-bg, #f5f8fc);
    border-radius: 6px;
  }
  .condition-heading {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px 16px;
    margin-bottom: 12px;
  }
  .condition-row {
    display: grid;
    grid-template-columns: 120px 85px minmax(0, 1fr) 50px;
    gap: 8px;
  }
  .condition-row :deep(.ant-select) {
    width: 100%;
    min-width: 0;
  }
  .condition-entry {
    margin-bottom: 12px;
  }
  .condition-note {
    margin: 8px 0;
    color: #66758a;
    font-size: 12px;
  }
  .field-error {
    margin: 6px 0 0;
    color: #cf1322;
    font-size: 12px;
  }
  @media (max-width: 600px) {
    .condition-row {
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    }
    .condition-row :deep(.ant-select:nth-child(3)) {
      grid-column: 1 / -1;
      grid-row: 2;
    }
    .condition-row :deep(.ant-btn) {
      grid-column: 2;
      justify-self: end;
    }
    .offer-conditions {
      padding: 12px;
    }
  }
</style>
