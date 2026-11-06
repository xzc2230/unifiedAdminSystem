<template>
  <BasicModal
    v-bind="$attrs"
    @register="registerModal"
    :title="title"
    :width="600"
    :confirmLoading="saving"
    :okButtonProps="{ disabled: !ready || saving }"
    :maskClosable="false"
    @ok="submit"
    @cancel="invalidate"
  >
    <a-alert v-if="error" type="error" show-icon :message="error" class="editor-hint" />
    <a-alert
      v-if="context?.kind === 'member'"
      type="info"
      show-icon
      message="填写当前租户内用户的实际用户 ID；不能移除或降级自己。"
      class="editor-hint"
    />
    <BasicForm @register="registerForm" />
  </BasicModal>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, ref, watch } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import { BasicForm, useForm, type FormSchema } from '/@/components/Form';
  import { usePermission } from '/@/hooks/web/usePermission';
  import { useUserStore } from '/@/store/modules/user';
  import {
    addTarget,
    bindDomain,
    createSite,
    flagOn,
    putMember,
    updateSite,
    updateTarget,
    type EditorContext,
    type Environment,
    type Member,
    type SiteRole,
    type Target,
  } from './resources.api';

  const props = defineProps<{ contextKey: number }>();
  const emit = defineEmits(['register', 'saved']);
  const userStore = useUserStore();
  const tenantKey = computed(() => JSON.stringify([userStore.getTenant, userStore.shareTenantId]));
  const { hasPermission } = usePermission();
  const context = ref<EditorContext>();
  const saving = ref(false);
  const ready = ref(false);
  const error = ref('');
  let generation = 0;
  const title = computed(() => {
    const data = context.value;
    if (data?.kind === 'site') return data.site ? '编辑站点登记' : '登记站点';
    if (data?.kind === 'target') return data.record ? '编辑目标地址' : '登记目标地址';
    if (data?.kind === 'member') return data.record ? '调整成员角色' : '添加站点成员';
    return '绑定域名';
  });
  const [registerForm, { resetFields, resetSchema, setFieldsValue, validate }] = useForm({
    schemas: [],
    labelWidth: 100,
    baseColProps: { span: 24 },
    showActionButtonGroup: false,
  });
  const [registerModal, { closeModal }] = useModalInner(async (data: EditorContext) => {
    if (data.contextKey !== props.contextKey) return;
    const version = ++generation;
    ready.value = false;
    saving.value = false;
    error.value = '';
    context.value = data;
    await resetFields();
    if (version !== generation || data.contextKey !== props.contextKey) return;
    const input = (field: string, label: string, maxLength: number): FormSchema => ({
      field,
      label,
      component: 'Input',
      required: true,
      componentProps: { maxLength },
      rules: [{ required: true, whitespace: true, message: `请输入${label}` }],
    });
    const enabled: FormSchema = {
      field: 'enabled',
      label: '登记状态',
      component: 'Switch',
      componentProps: { checkedChildren: '启用', unCheckedChildren: '停用' },
    };
    let schemas: FormSchema[];
    let values: Record<string, unknown>;
    if (data.kind === 'site') {
      schemas = [
        input('name', '站点名称', 100),
        {
          field: 'environment',
          label: '环境',
          component: 'Select',
          required: true,
          helpMessage: '创建后环境不可修改',
          componentProps: { disabled: !!data.site, options: ['dev', 'test', 'staging', 'prod'].map((value) => ({ label: value, value })) },
        },
        enabled,
      ];
      values = data.site
        ? { name: data.site.name, environment: data.site.environment, enabled: flagOn(data.site.enabled) }
        : { name: '', environment: 'dev', enabled: true };
    } else if (data.kind === 'target') {
      schemas = [
        input('name', '目标名称', 100),
        { ...input('url', '目标地址', 2000), helpMessage: 'HTTP/HTTPS 公网域名地址，不支持凭证或 fragment；仅登记，不发起连接。' },
        enabled,
      ];
      const target = data.record as Target | undefined;
      values = target ? { name: target.name, url: target.url, enabled: flagOn(target.enabled) } : { name: '', url: '', enabled: true };
    } else if (data.kind === 'domain') {
      schemas = [{ ...input('hostname', '域名', 253), helpMessage: '仅填写主机名，例如 www.example.com，不含协议、路径、端口或通配符。' }];
      values = { hostname: '' };
    } else {
      schemas = [
        { ...input('userId', '用户 ID', 32), componentProps: { maxLength: 32, disabled: !!data.record } },
        {
          field: 'role',
          label: '站点角色',
          component: 'Select',
          required: true,
          componentProps: {
            options: [
              { label: '管理员', value: 'ADMIN' },
              { label: '运营', value: 'EDITOR' },
              { label: '只读', value: 'VIEWER' },
            ],
          },
        },
      ];
      const member = data.record as Member | undefined;
      values = { userId: member?.userId ?? '', role: member?.role ?? 'VIEWER' };
    }
    await resetSchema(schemas);
    if (version !== generation) return;
    await setFieldsValue(values);
    if (version === generation) ready.value = true;
  });

  function invalidate() {
    generation++;
    ready.value = false;
    saving.value = false;
    error.value = '';
    context.value = undefined;
  }
  watch(
    [() => props.contextKey, tenantKey],
    () => {
      invalidate();
      closeModal();
    },
    { flush: 'sync' }
  );
  onBeforeUnmount(invalidate);

  function permitted(data: EditorContext) {
    if (data.kind === 'site' && !data.site) return hasPermission('siteplatform:create');
    if (data.kind === 'member') return data.site?.myRole === 'ADMIN' && hasPermission('siteplatform:manage');
    return !!data.site && ['ADMIN', 'EDITOR'].includes(data.site.myRole) && hasPermission('siteplatform:write');
  }
  async function submit() {
    const data = context.value;
    if (!data || saving.value || !ready.value || !permitted(data)) return;
    const version = generation;
    const tenant = tenantKey.value;
    const current = () => version === generation && tenant === tenantKey.value && data.contextKey === props.contextKey;
    saving.value = true;
    let values;
    try {
      values = await validate();
    } catch {
      if (current()) saving.value = false;
      return;
    }
    if (!current()) return;
    error.value = '';
    try {
      if (data.kind === 'site') {
        const input = {
          name: values.name.trim(),
          environment: (data.site?.environment ?? values.environment) as Environment,
          enabled: !!values.enabled,
        };
        if (data.site) await updateSite(data.site.id, input);
        else await createSite(input);
      } else if (data.kind === 'target' && data.site) {
        const input = { name: values.name.trim(), url: values.url.trim(), enabled: !!values.enabled };
        if (data.record) await updateTarget(data.site.id, (data.record as Target).id, input);
        else await addTarget(data.site.id, input);
      } else if (data.kind === 'domain' && data.site) {
        await bindDomain(data.site.id, { hostname: values.hostname.trim() });
      } else if (data.kind === 'member' && data.site) {
        const userId = values.userId.trim();
        if (userId === userStore.getUserInfo.id && values.role !== 'ADMIN') {
          error.value = '不能降级自己的管理员角色';
          return;
        }
        await putMember(data.site.id, { userId, role: values.role as SiteRole });
      } else return;
      if (!current()) return;
      closeModal();
      emit('saved', data);
      invalidate();
    } catch {
      if (current()) {
        const hints = {
          site: '请检查站点名称、环境和操作权限。',
          target: '请检查目标名称、HTTP/HTTPS 公网域名地址和操作权限。',
          domain: '请检查域名格式、是否已被占用以及操作权限。',
          member: '用户必须是当前租户的正常成员，且不能移除或降级自己。',
        };
        error.value = `保存失败，请重试。${hints[data.kind]}`;
      }
    } finally {
      if (current()) saving.value = false;
    }
  }
</script>

<style scoped lang="less">
  .editor-hint {
    margin-bottom: 16px;
  }
</style>
