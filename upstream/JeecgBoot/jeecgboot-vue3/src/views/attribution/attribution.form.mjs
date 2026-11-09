export const osOptions = [
  { label: 'Android', value: 'ANDROID' },
  { label: 'iOS', value: 'IOS' },
  { label: 'Web', value: 'WEB' },
];

export function newOffer(count, clientKey) {
  return { clientKey, targetUrl: '', weight: count === 0 ? 1 : 0, matchMode: 'AND', conditions: [] };
}

export function hydrateForm(source, makeKey) {
  return {
    applicationName: source.applicationName ?? '',
    channelName: source.channelName ?? '',
    fallbackUrl: source.fallbackUrl ?? '',
    ...(source.expectedConfigVersion !== undefined ? { expectedConfigVersion: source.expectedConfigVersion } : {}),
    managedBindingIds: [...(source.managedBindingIds ?? [])],
    offers: [...(source.offers ?? [])]
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((item) => ({
        ...item,
        clientKey: item.clientKey || makeKey(),
        conditions: item.conditions.map((condition) => ({ ...condition, values: [...condition.values] })),
      })),
  };
}

export function buildPayload(form) {
  const payload = {
    applicationName: form.applicationName.trim(),
    channelName: form.channelName.trim(),
    fallbackUrl: form.fallbackUrl.trim(),
    offers: form.offers.map((offer) => ({
      clientKey: offer.clientKey,
      ...(offer.bindingId ? { bindingId: offer.bindingId } : {}),
      targetUrl: offer.targetUrl.trim(),
      weight: offer.weight,
      matchMode: offer.matchMode,
      conditions: offer.conditions.map((condition) => ({ field: condition.field, operator: condition.operator, values: [...condition.values] })),
    })),
  };
  if (form.expectedConfigVersion !== undefined) {
    payload.expectedConfigVersion = form.expectedConfigVersion;
    const retained = new Set(form.offers.map((offer) => offer.bindingId).filter(Boolean));
    payload.removedBindingIds = (form.managedBindingIds ?? []).filter((id) => !retained.has(id));
  }
  return payload;
}

function validUrl(value) {
  try {
    const url = new URL(value);
    return (
      value.length <= 2048 &&
      url.protocol === 'https:' &&
      Boolean(url.hostname) &&
      !url.username &&
      !url.password &&
      !/[\u0000-\u0020\u007f]/.test(value.trim())
    );
  } catch {
    return false;
  }
}

export function validateForm(form, { countryEnabled = false, originalOffers = [] } = {}) {
  const errors = [];
  const add = (path, message) => errors.push({ path, message });
  for (const [field, label] of [
    ['applicationName', '应用名'],
    ['channelName', '渠道名'],
  ]) {
    if (!form[field]?.trim() || form[field].trim().length > 100) add(field, `${label}须为 1–100 个字符`);
  }
  if (!validUrl(form.fallbackUrl)) add('fallbackUrl', '请输入有效的 HTTPS 兜底地址');
  if (form.offers.length && !form.offers.some((offer) => Number.isSafeInteger(offer.weight) && offer.weight > 0)) {
    add('offers', '至少一个 Offer 权重大于 0');
  }
  const keys = new Set();
  const bindings = new Set();
  form.offers.forEach((offer, index) => {
    const path = `offers[${index}]`;
    if (!offer.clientKey || keys.has(offer.clientKey)) add(path, 'Offer 标识重复，请移除重复卡片后重试');
    keys.add(offer.clientKey);
    if (offer.bindingId && bindings.has(offer.bindingId)) add(path, 'Offer 绑定重复');
    if (offer.bindingId) bindings.add(offer.bindingId);
    if (!validUrl(offer.targetUrl)) add(`${path}.targetUrl`, '请输入有效的 HTTPS Offer 地址');
    if (!Number.isSafeInteger(offer.weight) || offer.weight < 0) add(`${path}.weight`, '权重须为非负整数');
    if (!['AND', 'OR'].includes(offer.matchMode)) add(`${path}.matchMode`, '请选择本条条件关系');
    offer.conditions.forEach((condition, conditionIndex) => {
      const prefix = `${path}.conditions[${conditionIndex}]`;
      if (!['OS', 'COUNTRY'].includes(condition.field)) add(`${prefix}.field`, '请选择支持的条件字段');
      if (!['IS', 'IS_NOT'].includes(condition.operator)) add(`${prefix}.operator`, '请选择是或不是');
      if (!Array.isArray(condition.values) || !condition.values.length) add(`${prefix}.values`, '请至少选择一个条件值');
      else if (condition.field === 'OS' && condition.values.some((value) => !osOptions.some((option) => option.value === value))) {
        add(`${prefix}.values`, '操作系统仅支持 Android、iOS、Web');
      } else if (condition.field === 'COUNTRY' && condition.values.some((value) => !/^[A-Z]{2}$/.test(value))) {
        add(`${prefix}.values`, '国家/地区须为 ISO 两字母代码，如 CN、US');
      }
      if (condition.field === 'COUNTRY' && !countryEnabled) {
        const original = originalOffers.find((item) => item.bindingId && item.bindingId === offer.bindingId);
        const unchanged = original?.conditions.some((item) => JSON.stringify(item) === JSON.stringify(condition));
        if (!unchanged) add(prefix, '国家/地区条件尚未开放，已有条件仅可保留或移除');
      }
    });
  });
  return errors;
}

// Each modal session owns an attempt. Failed requests retain their key until the serialized payload changes.
export function createSaveAttempt(makeKey) {
  let fingerprint;
  let key;
  return {
    prepare(payload) {
      const next = JSON.stringify(payload);
      if (next !== fingerprint) {
        fingerprint = next;
        key = makeKey();
      }
      return { key, payload: JSON.parse(next) };
    },
  };
}

export function readError(raw, offers = []) {
  const body = raw?.response?.data ?? raw ?? {};
  const code = raw?.response?.status ?? body.code;
  const fieldErrors = body.fieldErrors ?? body.result?.fieldErrors ?? [];
  return {
    message: body.message || '请求失败，请检查网络后重试',
    conflict: Number(code) === 409,
    fieldErrors: (Array.isArray(fieldErrors) ? fieldErrors : []).map((error) => {
      const index = error.clientKey ? offers.findIndex((offer) => offer.clientKey === error.clientKey) : -1;
      let path = error.path ?? '';
      if (index >= 0) path = `offers[${index}]${path ? `.${path.replace(/^offers\[\d+\]\.?/, '')}` : ''}`;
      return { ...error, path };
    }),
  };
}

export function conditionSummary(offer) {
  if (!offer.conditions.length) return '不限';
  return offer.conditions
    .map((condition) => {
      const label = condition.field === 'OS' ? '操作系统' : '国家/地区';
      const values = condition.values.map((value) => osOptions.find((item) => item.value === value)?.label ?? value);
      return `${label} ${condition.operator === 'IS_NOT' ? '不是' : '是'} ${values.join('、')}`;
    })
    .join(offer.matchMode === 'OR' ? ' 或 ' : ' 且 ');
}
