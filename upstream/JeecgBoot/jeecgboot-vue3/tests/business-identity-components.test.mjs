import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';

const directory = new URL('../src/views/system/businessIdentity/', import.meta.url);
const transpile = (source) =>
  ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
const behaviorModule = { exports: {} };
new Function('exports', transpile(readFileSync(new URL('identity.behavior.ts', directory), 'utf8')))(behaviorModule.exports);
const helpers = behaviorModule.exports;
const renderer = vue.createRenderer({
  createElement: (type) => ({ type, children: [] }),
  createText: (text) => ({ text }),
  createComment: () => ({}),
  insert: () => {},
  remove: () => {},
  setText: () => {},
  setElementText: () => {},
  parentNode: () => null,
  nextSibling: () => null,
  patchProp: () => {},
});
const flush = async () => {
  await new Promise(setImmediate);
  await vue.nextTick();
};
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};
const row = (tenantId = 3, userId = 'u1') => ({
  tenantId,
  userId,
  tenantName: '租户',
  username: userId,
  realname: '张三',
  tenantActive: true,
  userStatus: 1,
  memberStatus: 1,
  legacyUserId: null,
  identityState: 'UNOPENED',
  source: null,
});
const preview = (legacyUserId = '9007199254740993') => ({
  legacyUserId,
  available: true,
  conflict: null,
  resourceCounts: [{ resourceType: 'pkg_app_manager', count: 5 }],
  ownershipConflicts: [],
});

function mount(filename, overrides = {}) {
  const { descriptor, errors } = parse(readFileSync(new URL(filename, directory), 'utf8'), { filename });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: filename });
  const template = compileTemplate({
    source: descriptor.template.content,
    filename,
    id: filename,
    compilerOptions: { bindingMetadata: compiled.bindings },
  });
  assert.deepEqual(template.errors, []);
  const api = {
    getTenants: async () => [{ tenantId: 3, tenantName: '租户', status: 1 }],
    getIdentities: async () => ({ records: [row()], total: 1 }),
    getLegacyPreview: async () => preview(),
    openIdentity: async () => row(),
    bindIdentity: async () => row(),
    changeIdentityStatus: async () => row(),
    getIdentityAudit: async () => ({ records: [], total: 0 }),
    ...overrides,
  };
  let open,
    closed = 0,
    permitted = true;
  const deps = {
    vue,
    './identity.behavior': helpers,
    './identity.api': api,
    './identity.data': { columns: [], auditColumns: [], stateOptions: [], stateLabels: {}, stateColors: {}, stateLabel: (v) => v },
    '/@/components/Modal': {
      BasicModal: {},
      useModal: () => [() => {}, { openModal: () => {} }],
      useModalInner: (fn) => {
        open = fn;
        return [
          () => {},
          {
            closeModal: () => {
              closed++;
            },
            setModalProps: () => {},
          },
        ];
      },
    },
    '/@/components/Drawer': {
      BasicDrawer: {},
      useDrawer: () => [() => {}, { openDrawer: () => {} }],
      useDrawerInner: (fn) => {
        open = fn;
        return [
          () => {},
          {
            closeDrawer: () => {
              closed++;
            },
          },
        ];
      },
    },
    '/@/components/Table': { BasicTable: {}, TableAction: {}, useTable: () => [() => {}, {}] },
    '/@/hooks/web/usePermission': { usePermission: () => ({ hasPermission: () => permitted }) },
    '/@/hooks/web/useMessage': { useMessage: () => ({ createMessage: { success: () => {} } }) },
  };
  const module = { exports: {} };
  new Function('require', 'module', 'exports', transpile(compiled.content))(
    (id) => {
      if (id.endsWith('.vue')) return {};
      assert.ok(deps[id], `Unexpected dependency: ${id}`);
      return deps[id];
    },
    module,
    module.exports
  );
  const component = module.exports.default;
  component.render = () => null;
  const app = renderer.createApp(component);
  app.mount({ children: [] });
  return {
    state: app._instance.setupState,
    open: (data) => open(data),
    closed: () => closed,
    deny: () => {
      permitted = false;
    },
    unmount: () => app.unmount(),
  };
}

test('tenant selection is explicit; list sends contract filters and pagination', async () => {
  const calls = [];
  const view = mount('index.vue', {
    getIdentities: async (params) => {
      calls.push(params);
      return { records: [row()], total: 60 };
    },
  });
  await flush();
  assert.equal(calls.length, 0);
  assert.equal(view.state.tenantOptions[0].label, '租户（3）');
  view.state.tenantId = 3;
  await flush();
  view.state.keyword = ' 张三 ';
  view.state.stateFilter = 'UNOPENED';
  await view.state.search();
  assert.deepEqual(calls.at(-1), { tenantId: 3, keyword: '张三', state: 'UNOPENED', pageNo: 1, pageSize: 20 });
  await view.state.changePage({ current: 2, pageSize: 20 });
  assert.equal(calls.at(-1).pageNo, 2);
  view.unmount();
});

test('invalid targets disable writes but keep audit available', async () => {
  const view = mount('index.vue');
  await flush();
  for (const invalid of [{ userStatus: 2 }, { memberStatus: 0 }, { tenantActive: false }]) {
    const actions = view.state.getActions({ ...row(), ...invalid });
    assert.equal(actions.find((action) => action.label === '开通').disabled, true);
    assert.equal(actions.find((action) => action.label === '历史绑定').disabled, true);
    assert.equal(actions.find((action) => action.label === '停用').disabled, true);
    assert.equal(actions.find((action) => action.label === '操作记录').disabled, false);
  }
  view.unmount();
});

test('conflict arrays display their count and cannot authorize binding', async () => {
  const conflicts = [{ resourceType: 'pkg_app_manager', resourceId: 'app-1', tenantId: 4, ownerUserId: 'u2' }];
  const view = mount('IdentityModal.vue', { getLegacyPreview: async () => ({ ...preview(), ownershipConflicts: conflicts }) });
  view.open({ mode: 'bind', record: row() });
  view.state.form.legacyUserId = '9007199254740993';
  await view.state.loadPreview();
  assert.equal(view.state.ownershipConflictCount, 1);
  assert.equal(view.state.previewAllowed, false);
  view.unmount();
});

test('a late write after forced closure cannot close a newer session and releases the lock', async () => {
  const pending = deferred();
  const view = mount('IdentityModal.vue', { openIdentity: () => pending.promise });
  view.open({ mode: 'open', record: row() });
  view.state.form.reason = '开户';
  const request = view.state.submit();
  view.state.onVisibleChange(false);
  pending.resolve(row());
  await request;
  assert.equal(view.closed(), 0);
  assert.equal(view.state.saving, false);
  view.open({ mode: 'open', record: row(4, 'u2') });
  assert.equal(view.state.record.userId, 'u2');
  view.unmount();
});

test('late list success and failure cannot replace a newer tenant result or loading', async () => {
  for (const fails of [false, true]) {
    const first = deferred(),
      second = deferred();
    const view = mount('index.vue', { getIdentities: ({ tenantId }) => (tenantId === 3 ? first.promise : second.promise) });
    await flush();
    view.state.tenantId = 3;
    await flush();
    view.state.tenantId = 4;
    await flush();
    if (fails) first.reject(new Error('旧请求失败'));
    else first.resolve({ records: [row(3)], total: 1 });
    await flush();
    assert.equal(view.state.loading, true);
    assert.deepEqual(view.state.rows, []);
    second.resolve({ records: [row(4)], total: 1 });
    await flush();
    assert.equal(view.state.rows[0].tenantId, 4);
    assert.equal(view.state.listError, '');
    assert.equal(view.state.loading, false);
    view.unmount();
  }
});

test('a failed list clears rows and remains retryable; clearing tenant invalidates pending list', async () => {
  let fail = true;
  const pending = deferred();
  const view = mount('index.vue', {
    getIdentities: async () => {
      if (fail) throw new Error('读取失败');
      return pending.promise;
    },
  });
  await flush();
  view.state.tenantId = 3;
  await flush();
  assert.match(view.state.listError, /读取失败/);
  fail = false;
  const request = view.state.loadList();
  view.state.tenantId = undefined;
  await flush();
  pending.resolve({ records: [row()], total: 1 });
  await request;
  assert.deepEqual(view.state.rows, []);
  assert.equal(view.state.loading, false);
  view.unmount();
});

test('edited or closed binding rejects late preview and resets explicit confirmation', async () => {
  const pending = deferred();
  const view = mount('IdentityModal.vue', { getLegacyPreview: () => pending.promise });
  view.open({ mode: 'bind', record: row() });
  view.state.form.legacyUserId = '9007199254740993';
  const request = view.state.loadPreview();
  view.state.form.legacyUserId = '2';
  pending.resolve(preview());
  await request;
  assert.equal(view.state.preview, null);
  assert.equal(view.state.previewLoading, false);
  await view.state.loadPreview();
  view.state.confirmed = true;
  view.state.form.reason = '编辑原因';
  assert.equal(view.state.confirmed, false);
  assert.equal(view.state.preview, null);
  view.unmount();
});

test('preview from an old modal session cannot authorize the next target', async () => {
  const pending = deferred();
  const view = mount('IdentityModal.vue', { getLegacyPreview: () => pending.promise });
  view.open({ mode: 'bind', record: row() });
  view.state.form.legacyUserId = '9007199254740993';
  const request = view.state.loadPreview();
  view.state.onVisibleChange(false);
  view.open({ mode: 'bind', record: row(4, 'u2') });
  pending.resolve(preview());
  await request;
  assert.equal(view.state.preview, null);
  assert.equal(view.state.record.userId, 'u2');
  view.unmount();
});

test('binding needs exact preview, reason, and explicit confirmation; write preserves string ID', async () => {
  const calls = [];
  const view = mount('IdentityModal.vue', {
    bindIdentity: async (data) => {
      calls.push(data);
      return row();
    },
  });
  view.open({ mode: 'bind', record: row() });
  view.state.form.legacyUserId = '9007199254740993';
  view.state.form.reason = '核对历史归属';
  await view.state.submit();
  assert.equal(calls.length, 0);
  await view.state.loadPreview();
  await view.state.submit();
  assert.equal(calls.length, 0);
  view.state.confirmed = true;
  await view.state.submit();
  assert.deepEqual(calls, [{ tenantId: 3, userId: 'u1', legacyUserId: '9007199254740993', reason: '核对历史归属' }]);
  view.unmount();
});

test('duplicate open clicks submit once; failure retains reason and unlocks retry', async () => {
  const pending = deferred();
  const calls = [];
  const view = mount('IdentityModal.vue', {
    openIdentity: (data) => {
      calls.push(data);
      return pending.promise;
    },
  });
  view.open({ mode: 'open', record: row() });
  view.state.form.reason = '新员工开户';
  const first = view.state.submit();
  await view.state.submit();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], { tenantId: 3, userId: 'u1', reason: '新员工开户' });
  assert.equal(await view.state.canClose(), false);
  pending.reject(new Error('操作失败'));
  await first;
  assert.equal(view.closed(), 0);
  assert.equal(view.state.form.reason, '新员工开户');
  assert.equal(view.state.saving, false);
  assert.match(view.state.submitError, /操作失败/);
  view.deny();
  await view.state.submit();
  assert.equal(calls.length, 1);
  view.unmount();
});

test('status sends explicit enabled false/true and rejects empty or excessive reason', async () => {
  const calls = [];
  const view = mount('IdentityModal.vue', {
    changeIdentityStatus: async (data) => {
      calls.push(data);
      return row();
    },
  });
  for (const [identityState, enabled] of [
    ['ACTIVE', false],
    ['DISABLED', true],
  ]) {
    view.open({ mode: 'status', record: { ...row(), identityState, legacyUserId: '9223372036854775807' } });
    await view.state.submit();
    view.state.form.reason = 'a'.repeat(501);
    await view.state.submit();
    assert.equal(calls.length, enabled ? 1 : 0);
    view.state.form.reason = '管理员核实';
    await view.state.submit();
    assert.deepEqual(calls.at(-1), { tenantId: 3, userId: 'u1', enabled, reason: '管理员核实' });
  }
  view.unmount();
});

test('audit drawer uses selected target and ignores old target replies', async () => {
  const first = deferred(),
    second = deferred(),
    calls = [];
  const view = mount('IdentityAuditDrawer.vue', {
    getIdentityAudit: (params) => {
      calls.push(params);
      return params.userId === 'u1' ? first.promise : second.promise;
    },
  });
  view.open({ record: row() });
  view.open({ record: row(4, 'u2') });
  second.resolve({ records: [{ id: 'new', reason: '核实' }], total: 1 });
  await flush();
  first.resolve({ records: [{ id: 'old' }], total: 1 });
  await flush();
  assert.equal(view.state.rows[0].id, 'new');
  assert.deepEqual(calls[1], { tenantId: 4, userId: 'u2', pageNo: 1, pageSize: 20 });
  view.unmount();
});
