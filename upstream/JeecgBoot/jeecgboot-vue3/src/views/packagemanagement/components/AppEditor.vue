<template>
  <div class="app-editor">
    <section class="editor-section" aria-labelledby="app-basic-heading">
      <header class="section-heading">
        <div><h3 id="app-basic-heading">基础信息</h3><p>设置应用标识与访问链接</p></div>
        <span class="section-caption">带 * 的为必填项</span>
      </header>
      <div class="field-grid basic-grid identity-row">
        <a-form-item
          v-if="platformField"
          :label="platformField.label"
          name="platform"
          :rules="rulesFor(platformField)"
          class="span-third platform-form-item"
        >
          <a-radio-group :value="model.platform || 'android'" class="platform-options" @change="onPlatformChange($event.target.value)">
            <a-radio v-for="option in platformOptions" :key="String(option.value)" :value="option.value">{{ option.label }}</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
          v-for="field in identityFields"
          :key="field.key"
          class="span-third"
          :label="field.label"
          :name="field.key"
          :rules="rulesFor(field)"
        >
          <a-input
            :value="model[field.key]"
            :disabled="Boolean(field.readonly || (field.immutable && editing))"
            :placeholder="placeholders[field.key]"
            allowClear
            @update:value="update(field, $event)"
          />
        </a-form-item>
      </div>
      <div v-if="model.platform === 'ios'" class="field-grid ios-store-fields">
        <a-form-item
          v-if="appleAppIdField"
          :label="appleAppIdField.label"
          name="appleAppId"
          :rules="rulesFor(appleAppIdField)"
          extra="商店查询会自动拼接 id=，这里只填数字。"
        >
          <a-input
            :value="model.appleAppId"
            class="apple-id-input"
            placeholder="只输入数字，不要带 id"
            allowClear
            @update:value="update(appleAppIdField, String($event || '').replace(/\D/g, ''))"
          />
        </a-form-item>
        <a-form-item
          v-if="storeRegionField"
          :label="storeRegionField.label"
          name="storeRegion"
          :rules="rulesFor(storeRegionField)"
        >
          <a-select
            :value="model.storeRegion || 'us'"
            :options="optionsFor(storeRegionField)"
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="不选则默认美国"
            @change="update(storeRegionField, $event || 'us')"
          />
        </a-form-item>
      </div>
      <div class="field-grid basic-grid">
        <a-form-item
          v-for="field in basicFields"
          :key="field.key"
          :class="basicSpan(field.key)"
          :label="field.label"
          :name="field.key"
          :rules="rulesFor(field)"
        >
          <a-textarea
            v-if="field.key === 'mark'"
            :value="model[field.key]"
            :rows="2"
            placeholder="填写应用备注（选填）"
            @update:value="update(field, $event)"
          />
          <a-input
            v-else
            :value="model[field.key]"
            :disabled="Boolean(field.readonly || (field.immutable && editing))"
            :placeholder="placeholders[field.key]"
            allowClear
            @update:value="update(field, $event)"
          />
        </a-form-item>
      </div>
    </section>

    <section class="editor-section" aria-labelledby="app-access-heading">
      <header class="section-heading"
        ><div><h3 id="app-access-heading">访问控制</h3><p>选择风控模式，配置地区访问规则</p></div></header
      >
      <a-form-item v-if="statusField" label="风控模式" name="status" :rules="rulesFor(statusField)" class="mode-form-item">
        <a-radio-group :value="model.status" class="mode-options" @change="update(statusField, $event.target.value)">
          <a-radio
            v-for="option in optionsFor(statusField)"
            :key="option.value"
            :value="option.value"
            class="mode-option"
            :class="{ 'mode-option-selected': String(model.status) === String(option.value) }"
          >
            <span class="mode-name">{{ option.label }}</span>
            <span class="mode-description">{{ modeDescriptions[String(option.value)] || '使用该风控模式' }}</span>
          </a-radio>
        </a-radio-group>
        <p v-if="!optionsFor(statusField).length" class="field-help">暂无可用模式，请联系管理员配置应用状态字典。</p>
      </a-form-item>
      <div class="control-grid">
        <div v-for="field in toggleFields" :key="field.key" class="toggle-setting">
          <div
            ><label :for="`app-${field.key}`">{{ field.label }}</label
            ><p>{{ toggleDescriptions[field.key] }}</p></div
          >
          <a-form-item :name="field.key" :rules="rulesFor(field)" :aria-label="field.label" class="toggle-form-item">
            <a-switch
              :id="`app-${field.key}`"
              :checked="Number(model[field.key]) === 1"
              :disabled="!optionsFor(field).length"
              :aria-label="field.label"
              @change="onToggle(field, $event)"
            />
          </a-form-item>
        </div>
      </div>
      <p v-if="toggleFields.some((field) => !optionsFor(field).length)" class="field-help">开关选项暂不可用，请联系管理员配置应用开关字典。</p>
      <a-form-item
        v-if="areasField"
        :label="areasField.label"
        name="areas"
        :rules="rulesFor(areasField)"
        class="areas-form-item"
        :extra="whitelistOn ? '白名单已开启，请选择允许访问的地区。' : '白名单关闭时不能修改地区，已选地区会保留。'"
      >
        <a-select
          :value="model.areas"
          :options="optionsFor(areasField)"
          mode="multiple"
          allowClear
          showSearch
          optionFilterProp="label"
          :placeholder="whitelistOn ? '搜索并选择地区，可多选' : '请先开启白名单'"
          :disabled="!whitelistOn || !optionsFor(areasField).length"
          @change="update(areasField, $event)"
        />
      </a-form-item>
    </section>

    <section class="editor-section" aria-labelledby="app-risk-heading">
      <header class="section-heading">
        <div>
          <h3 id="app-risk-heading">风控阈值</h3>
          <p>{{ riskAdvancedOpen ? '按应用配置设备关联与切换限制' : '当前使用默认阈值' }}</p>
        </div>
        <button type="button" class="advanced-toggle" :aria-expanded="riskAdvancedOpen" aria-controls="app-risk-fields" @click="riskAdvancedOpen = !riskAdvancedOpen">
          {{ riskAdvancedOpen ? '收起' : '高级配置' }}
        </button>
      </header>
      <div id="app-risk-fields" v-show="riskAdvancedOpen">
      <div class="risk-notice">关联与切换阈值留空或设为 0 时使用默认值 1；计数窗口小于 60 秒时使用默认值 86400 秒（24 小时）。</div>
      <div class="field-grid">
        <a-form-item
          v-for="field in riskFields"
          :key="field.key"
          :label="riskLabels[field.key] || field.label"
          :name="field.key"
          :rules="rulesFor(field)"
          :extra="riskDescriptions[field.key]"
        >
          <a-input-number
            :value="model[field.key]"
            :min="0"
            :precision="0"
            :placeholder="field.key === 'riskWindowSeconds' ? '默认 86400' : '默认 1'"
            @change="update(field, $event)"
          >
            <template #addonAfter>{{ field.key === 'riskWindowSeconds' ? '秒' : field.key === 'aidGaidSwitchThreshold' ? '次' : '个' }}</template>
          </a-input-number>
        </a-form-item>
      </div>
      </div>
    </section>

    <section v-if="referrerField" class="editor-section" aria-labelledby="app-referrer-heading">
      <header class="section-heading"
        ><div><h3 id="app-referrer-heading">来源黑名单</h3><p>配置 InstallReferrer 黑名单模板</p></div
        ><span class="format-label">JSON</span></header
      >
      <a-form-item
        name="referrerBlacklistJson"
        :rules="rulesFor(referrerField)"
        class="json-form-item"
        extra="选填，请保持合法的 JSON 格式；留空时不设置自定义模板。"
      >
        <a-textarea
          :value="model.referrerBlacklistJson"
          :rows="8"
          :spellcheck="false"
          aria-label="InstallReferrer 黑名单模板（JSON）"
          class="json-editor"
          placeholder="输入 JSON 格式的黑名单模板"
          @update:value="update(referrerField, $event)"
        />
      </a-form-item>
    </section>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watchEffect } from 'vue';
  import type { PackageField, PackageOption } from '/@/types/packagemanagement';

  const props = defineProps<{
    model: Record<string, any>;
    fields: PackageField[];
    optionsFor: (field: PackageField) => PackageOption[];
    editing: boolean;
  }>();
  const emit = defineEmits<{ (event: 'update-field', field: PackageField, value: any): void }>();
  const update = (field: PackageField, value: any) => emit('update-field', field, value);
  const whitelistOn = computed(() => Number(props.model.whetherRestrict) === 1);
  const rulesFor = (field: PackageField) => {
    return field.required ? [{ required: true, message: `请填写${field.label}` }] : [];
  };
  const platformField = computed(() => props.fields.find((field) => field.key === 'platform'));
  const appleAppIdField = computed(() => props.fields.find((field) => field.key === 'appleAppId'));
  const storeRegionField = computed(() => props.fields.find((field) => field.key === 'storeRegion'));
  const platformOptions = computed<PackageOption[]>(
    () => platformField.value?.options || [{ value: 'android', label: '安卓' }, { value: 'ios', label: 'iOS' }]
  );
  watchEffect(() => {
    const field = platformField.value;
    if (!field) return;
    const current = props.model.platform;
    if (current == null || current === '') update(field, 'android');
  });
  watchEffect(() => {
    const field = storeRegionField.value;
    if (!field || props.model.platform !== 'ios') return;
    if (props.model.storeRegion == null || props.model.storeRegion === '') update(field, 'us');
  });
  watchEffect(() => {
    const field = props.fields.find((item) => item.key === 'applicationCode');
    if (!field || props.editing || props.model.applicationCode) return;
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let code = '';
    for (let index = 0; index < 6; index += 1) code += alphabet[Math.floor(Math.random() * alphabet.length)];
    update(field, code);
  });
  function onPlatformChange(value: string) {
    if (!platformField.value) return;
    update(platformField.value, value);
    if (value !== 'ios') {
      if (appleAppIdField.value) update(appleAppIdField.value, '');
      if (storeRegionField.value) update(storeRegionField.value, '');
    }
  }
  const identityFields = computed(() =>
    ['appName', 'packageCode'].flatMap((key) => props.fields.filter((field) => field.key === key))
  );
  const basicFields = computed(() =>
    ['applicationCode', 'url', 'mark'].flatMap((key) => props.fields.filter((field) => field.key === key))
  );
  function basicSpan(key: string) {
    return key === 'url' || key === 'mark' ? 'span-all' : 'span-third';
  }
  function hasCustomRisk(model: Record<string, any>) {
    const customCount = ['gaidAidThreshold', 'aidDeviceThreshold', 'aidGaidSwitchThreshold'].some((key) => {
      const value = Number(model[key]);
      return model[key] != null && model[key] !== '' && Number.isFinite(value) && value > 0 && value !== 1;
    });
    const windowValue = Number(model.riskWindowSeconds);
    const customWindow =
      model.riskWindowSeconds != null &&
      model.riskWindowSeconds !== '' &&
      Number.isFinite(windowValue) &&
      windowValue >= 60 &&
      windowValue !== 86400;
    return customCount || customWindow;
  }
  const riskAdvancedOpen = ref(hasCustomRisk(props.model));
  const statusField = computed(() => props.fields.find((field) => field.key === 'status'));
  const areasField = computed(() => props.fields.find((field) => field.key === 'areas'));
  function onToggle(field: PackageField, checked: boolean) {
    update(field, checked ? 1 : 0);
  }
  const toggleFields = computed(() => props.fields.filter((field) => ['whetherRestrict', 'whetherUs'].includes(field.key)));
  const riskFields = computed(() =>
    props.fields.filter((field) => ['gaidAidThreshold', 'aidDeviceThreshold', 'aidGaidSwitchThreshold', 'riskWindowSeconds'].includes(field.key))
  );
  const referrerField = computed(() => props.fields.find((field) => field.key === 'referrerBlacklistJson'));
  const placeholders: Record<string, string> = {
    appName: '输入应用名称',
    author: '输入出包人（选填）',
    applicationCode: '保存时生成 6 位唯一编码',
    userName: '当前登录用户',
    packageCode: '例如 com.example.app',
    url: 'https://example.com',
  };
  const modeDescriptions: Record<string, string> = {
    '0': '使用全关模式',
    '1': '使用全开模式',
    '2': '使用归因模式',
    '3': '用于调试与联调',
  };
  const toggleDescriptions: Record<string, string> = {
    whetherRestrict: '开启后才能选择白名单地区',
    whetherUs: '限制美国地区访问',
  };
  const riskLabels: Record<string, string> = {
    gaidAidThreshold: 'GAID 关联 AID 数',
    aidDeviceThreshold: 'AID 关联设备数',
    aidGaidSwitchThreshold: 'GAID 切换次数',
    riskWindowSeconds: '风控计数窗口',
  };
  const riskDescriptions: Record<string, string> = {
    gaidAidThreshold: '同一 GAID 可关联的 AID 数量阈值',
    aidDeviceThreshold: '同一 AID 可关联的设备数量阈值',
    aidGaidSwitchThreshold: '同一 AID 的 GAID 切换次数阈值',
    riskWindowSeconds: '设备关联与切换行为的统计时长',
  };
</script>

<style scoped lang="less">
  .app-editor {
    --editor-surface: #fff;
    --editor-border: #e4e9f0;
    --editor-text: #243247;
    --editor-muted: #66758a;
    display: grid;
    gap: 18px;
    color: var(--editor-text);
  }
  .editor-section {
    padding: 22px 24px 4px;
    border: 1px solid var(--editor-border);
    border-radius: 12px;
    background: var(--editor-surface);
  }
  .section-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 20px;
  }
  .section-heading h3 {
    margin: 0;
    color: var(--editor-text);
    font-size: 15px;
    font-weight: 600;
    line-height: 24px;
  }
  .section-heading p,
  .toggle-setting p {
    margin: 4px 0 0;
    color: var(--editor-muted);
    font-size: 12px;
    line-height: 20px;
  }
  .section-caption {
    padding-top: 3px;
    color: var(--editor-muted);
    font-size: 12px;
    white-space: nowrap;
  }
  .advanced-toggle {
    flex: none;
    margin-top: 2px;
    padding: 4px 12px;
    border: 1px solid #91caff;
    border-radius: 6px;
    background: #f0f6ff;
    color: #1677ff;
    font-size: 12px;
    line-height: 20px;
    cursor: pointer;
  }
  .advanced-toggle:hover {
    border-color: #1677ff;
  }
  .advanced-toggle:focus-visible {
    outline: 2px solid #1677ff;
    outline-offset: 2px;
  }
  .field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 22px;
  }
  .basic-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
  .span-third {
    grid-column: span 2;
  }
  .span-all {
    grid-column: 1 / -1;
  }
  .field-grid > *,
  .control-grid > * {
    min-width: 0;
  }
  .full-width {
    grid-column: 1 / -1;
  }
  .app-editor :deep(.ant-form-item) {
    margin-bottom: 20px;
  }
  .app-editor :deep(.ant-form-item-label) {
    padding-bottom: 6px;
  }
  .app-editor :deep(.ant-form-item-label > label) {
    height: auto;
    color: var(--editor-text);
    font-size: 13px;
    font-weight: 500;
  }
  .app-editor :deep(.ant-input-number) {
    width: 100%;
  }
  .app-editor .apple-id-input {
    width: 100%;
  }
  .platform-form-item {
    margin-bottom: 20px;
  }
  .platform-options {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    min-height: 32px;
  }
  .app-editor :deep(.ant-input-number-group-wrapper) {
    width: 100%;
  }
  .app-editor :deep(.ant-input),
  .app-editor :deep(.ant-input-affix-wrapper),
  .app-editor :deep(.ant-input-number),
  .app-editor :deep(.ant-select-selector) {
    border-radius: 6px;
  }
  .app-editor :deep(.ant-form-item-extra) {
    padding-top: 5px;
    color: var(--editor-muted);
    font-size: 12px;
    line-height: 20px;
  }
  .mode-options {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
  }
  .mode-option {
    display: flex;
    align-items: flex-start;
    margin: 0;
    padding: 14px 10px;
    border: 1px solid var(--editor-border);
    border-radius: 8px;
    transition:
      border-color 0.15s,
      background 0.15s;
  }
  .mode-option-selected {
    border-color: #1677ff;
    background: #f0f6ff;
  }
  .mode-option :deep(.ant-radio) {
    margin-top: 3px;
  }
  .mode-option :deep(.ant-radio + span) {
    min-width: 0;
    padding-inline-end: 0;
  }
  .mode-name {
    display: block;
    color: var(--editor-text);
    font-size: 13px;
    font-weight: 600;
  }
  .mode-description {
    display: block;
    margin-top: 4px;
    color: var(--editor-muted);
    font-size: 11px;
    line-height: 18px;
  }
  .control-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 20px;
  }
  .toggle-setting {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    padding: 14px;
    border: 1px solid var(--editor-border);
    border-radius: 8px;
  }
  .toggle-setting label {
    color: var(--editor-text);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
  }
  .toggle-setting p {
    font-size: 11px;
  }
  .app-editor .toggle-form-item {
    flex: none;
    margin: 0;
  }
  .risk-notice {
    margin-bottom: 18px;
    padding: 10px 12px;
    border-left: 3px solid #91caff;
    border-radius: 4px;
    background: #f0f6ff;
    color: var(--editor-muted);
    font-size: 12px;
    line-height: 20px;
  }
  .format-label {
    padding: 2px 8px;
    border: 1px solid var(--editor-border);
    border-radius: 4px;
    color: var(--editor-muted);
    font:
      11px/18px Consolas,
      monospace;
  }
  .json-editor {
    padding: 12px 14px;
    background: #fafbfd;
    font:
      12px/1.8 Consolas,
      'SFMono-Regular',
      monospace;
    tab-size: 2;
  }
  .field-help {
    margin: 8px 0;
    color: var(--editor-muted);
    font-size: 12px;
  }
  :global(html[data-theme='dark'] .app-editor) {
    --editor-surface: #1f1f1f;
    --editor-border: #424242;
    --editor-text: #e5e7eb;
    --editor-muted: #a1aab8;
  }
  :global(html[data-theme='dark'] .app-editor .mode-option-selected),
  :global(html[data-theme='dark'] .app-editor .risk-notice),
  :global(html[data-theme='dark'] .app-editor .advanced-toggle) {
    background: #162c46;
  }
  :global(html[data-theme='dark'] .app-editor .json-editor) {
    background: #181818;
  }
  @media (max-width: 640px) {
    .editor-section {
      padding: 16px 14px 0;
      border-radius: 8px;
    }
    .field-grid,
    .basic-grid,
    .control-grid {
      grid-template-columns: minmax(0, 1fr);
    }
    .span-third,
    .span-all {
      grid-column: 1 / -1;
    }
    .mode-options {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
    .section-caption {
      display: none;
    }
  }
  @media (prefers-reduced-motion: reduce) {
    .mode-option {
      transition: none;
    }
  }
</style>
