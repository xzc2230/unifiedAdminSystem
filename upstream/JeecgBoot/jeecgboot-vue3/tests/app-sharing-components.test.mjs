import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import { renderToString } from 'vue/server-renderer';
import * as dev from '../src/views/packagemanagement/package-dev.mjs';
import * as regions from '../src/views/packagemanagement/package-regions.mjs';
import * as conversions from '../src/views/packagemanagement/package-conversions.mjs';

const directory = new URL('../src/views/packagemanagement/components/', import.meta.url);
const transpile = (source) =>
  ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const renderer = vue.createRenderer({
  createElement: (type) => ({ type, children: [] }),
  createText: (text) => ({ text }),
  createComment: () => ({}),
  insert() {},
  remove() {},
  setText() {},
  setElementText() {},
  parentNode: () => null,
  nextSibling: () => null,
  patchProp() {},
});
const flush = async () => {
  await new Promise(setImmediate);
  await vue.nextTick();
};
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
};
const owner = (id = '9007199254740993') => ({ id, canManage: true });

test('sharing modal only asks for one recipient and does not render share lists', () => {
  const source = readFileSync(new URL('AppSharingModal.vue', directory), 'utf8');
  const template = source.match(/<template>([\s\S]*?)<\/template>/)[1];
  assert.match(template, /label="接收人"/);
  assert.match(template, /title="转移"/);
  assert.doesNotMatch(template, /共享应用/);
  assert.doesNotMatch(source, /<a-table|共享原因|撤销共享|共享审计/);
});

function mount(filename = 'AppSharingModal.vue', overrides = {}, domain = 'apps') {
  const file = new URL(filename, directory);
  assert.ok(existsSync(file), 'independent sharing component must exist');
  const { descriptor, errors } = parse(readFileSync(file, 'utf8'), { filename });
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: filename });
  assert.deepEqual(
    compileTemplate({ source: descriptor.template.content, filename, id: filename, compilerOptions: { bindingMetadata: compiled.bindings } }).errors,
    []
  );
  let table,
    confirmations = [],
    reloads = 0;
  const permitted = vue.ref(true);
  const emitted = [];
  const api = {
    transferTargets: async () => [{ id: 'u1', displayName: '张三' }],
    shareApps: async () => 1,
    transferApps: async () => 1,
    revokeAppShares: async () => 1,
    getAppShares: async () => ({ records: [], total: 0 }),
    getAppShareAudit: async () => ({ records: [], total: 0 }),
    packageApi: () => ({ list: async () => ({ records: [], total: 0 }), remove: async () => {} }),
    ...overrides,
  };
  const deps = {
    vue,
    '/@/api/packagemanagement': api,
    '/@/hooks/web/usePermission': { usePermission: () => ({ hasPermission: () => permitted.value }) },
    '/@/store/modules/permission': { usePermissionStore: () => ({ getPermCodeList: ['*:*:*'] }) },
    '/@/store/modules/user': { useUserStore: () => ({ getUserInfo: { username: 'current-user' } }) },
    '/@/api/packagemanagement/dev': {},
    '../package-dev.mjs': dev,
    '../package-regions.mjs': regions,
    '../package-conversions.mjs': conversions,
    'ant-design-vue': { message: { success() {}, warning() {}, error() {} }, Modal: { confirm: (config) => confirmations.push(config) } },
    '/@/hooks/web/useCopyToClipboard': {},
    '/@/utils/dict/JDictSelectUtil': { initDictOptions: async () => [] },
    '/@/hooks/web/useCachedPageRefresh': { useCachedPageRefresh: () => ({ trackRequest: request => request() }) },
    '/@/hooks/setting': { useGlobSetting: () => ({ domainUrl: '' }) },
    '/@/components/Table': {
      BasicTable: {},
      useTable: (config) => {
        table = config;
        return [
          () => {},
          {
            reload: async () => {
              reloads++;
            },
            clearSelectedRowKeys() {},
          },
        ];
      },
    },
  };
  const module = { exports: {} };
  new Function('require', 'module', 'exports', transpile(compiled.content))(
    (id) => {
      if (id.endsWith('.vue')) return {};
      assert.ok(deps[id], `Unexpected dependency ${id}`);
      return deps[id];
    },
    module,
    module.exports
  );
  const component = module.exports.default;
  component.render = () => null;
  const app = renderer.createApp(
    component,
    filename === 'PackagePage.vue' ? { definition: { domain, title: '应用', fields: [], filters: [] } } : { onSuccess: () => emitted.push('success') }
  );
  app.mount({ children: [] });
  return {
    state: app._instance.setupState,
    table: () => table,
    deny: () => {
      permitted.value = false;
    },
    confirmations,
    emitted,
    reloads: () => reloads,
    unmount: () => app.unmount(),
  };
}

test('page requires explicit row capability AND role permission; mixed and missing rows fail closed', async () => {
  const view = mount('PackagePage.vue');
  assert.equal(typeof view.state.openSharing, 'function');
  view.table().rowSelection.onChange(['1', '2'], [owner('1'), { id: '2', canManage: false }]);
  assert.equal(view.state.canRemoveSelected, false);
  view.state.confirmRemove(['1', '2']);
  assert.equal(view.confirmations.length, 0);
  view.table().rowSelection.onChange(['1'], [owner('1')]);
  assert.equal(view.state.canRemoveSelected, true);
  assert.equal(view.state.canManageRow({ id: '2' }), false);
  assert.equal(view.state.canManageRow({ id: '2', canManage: 'true' }), false);
  view.deny();
  view.state.confirmRemove(['1']);
  assert.equal(view.confirmations.length, 0);
  view.unmount();
});

test('modal requires one recipient, preserves the opened string ID, and sends a fixed reason', async () => {
  const calls = [];
  const view = mount(undefined, {
    transferApps: async (...args) => {
      calls.push(args);
      return 1;
    },
  });
  const rows = [owner()];
  await view.state.open(rows);
  assert.equal(view.state.targets[0].label, '张三');
  await view.state.grant();
  assert.equal(calls.length, 0);
  assert.match(view.state.submitError, /请选择接收人/);
  view.state.targetUser = 'u1';
  rows.push(owner('2'));
  await view.state.grant();
  assert.deepEqual(calls, [[['9007199254740993'], 'u1']]);
  assert.equal(view.emitted.length, 1);
  assert.equal(view.state.visible, false);
  await view.state.open([owner(), owner('2')]);
  assert.equal(view.state.visible, false);
  view.unmount();
});

test('modal refuses recipients, missing capabilities, invalid or oversized IDs and missing role', async () => {
  const view = mount();
  for (const rows of [
    [{ id: '1' }],
    [{ id: '1', canManage: false }],
    [owner('0')],
    [owner('-1')],
    [owner(9007199254740992)],
    [owner(), owner('2')],
    Array.from({ length: 101 }, (_, i) => owner(String(i + 1))),
  ]) {
    await view.state.open(rows);
    assert.equal(view.state.visible, false);
  }
  view.deny();
  await view.state.open([owner()]);
  assert.equal(view.state.visible, false);
  view.unmount();
});

test('grant duplicate lock and failure retain all inputs and permit retry', async () => {
  const pending = deferred();
  let calls = 0;
  const view = mount(undefined, {
    transferApps: () => {
      calls++;
      return pending.promise;
    },
  });
  await view.state.open([owner()]);
  view.state.targetUser = 'u1';
  const request = view.state.grant();
  await view.state.grant();
  assert.equal(calls, 1);
  assert.equal(view.state.saving, true);
  pending.reject(new Error('共享失败'));
  await request;
  assert.equal(view.state.targetUser, 'u1');
  assert.match(view.state.submitError, /共享失败/);
  assert.equal(view.state.saving, false);
  assert.equal(view.state.visible, true);
  view.deny();
  await view.state.grant();
  assert.equal(calls, 1);
  view.unmount();
});

test('closed session target or write success or failure cannot pollute a newer modal', async () => {
  for (const fail of [false, true]) {
    const oldTargets = deferred(),
      oldWrite = deferred(),
      newWrite = deferred();
    let targetCalls = 0,
      writes = 0;
    const view = mount(undefined, {
      transferTargets: () => (++targetCalls === 1 ? oldTargets.promise : Promise.resolve([{ id: 'u2', displayName: '新接收人' }])),
      transferApps: () => (++writes === 1 ? oldWrite.promise : newWrite.promise),
    });
    const oldOpen = view.state.open([owner('1')]);
    view.state.targetUser = 'u1';
    const request = view.state.grant();
    view.state.close();
    await view.state.open([owner('2')]);
    view.state.targetUser = 'u2';
    const newer = view.state.grant();
    if (fail) {
      oldTargets.reject(new Error('旧目标失败'));
      oldWrite.reject(new Error('旧写入失败'));
    } else {
      oldTargets.resolve([{ id: 'old', displayName: '旧接收人' }]);
      oldWrite.resolve(1);
    }
    await oldOpen;
    await request;
    await flush();
    assert.equal(view.state.visible, true);
    assert.equal(view.state.targets[0].value, 'u2');
    assert.equal(view.state.targetUser, 'u2');
    assert.equal(view.state.submitError, '');
    assert.equal(view.state.saving, true);
    assert.equal(view.emitted.length, 0);
    newWrite.resolve(1);
    await newer;
    assert.equal(view.emitted.length, 1);
    view.unmount();
  }
});

test('recipient load errors stay visible and can be retried', async () => {
  let fail = true;
  const view = mount(undefined, {
    transferTargets: async () => {
      if (fail) throw new Error('接收人加载失败');
      return [{ id: 'u1', displayName: '张三' }];
    },
  });
  await view.state.open([owner()]);
  assert.match(view.state.targetsError, /接收人加载失败/);
  fail = false;
  await view.state.loadTargets();
  assert.equal(view.state.targetsError, '');
  assert.equal(view.state.targets[0].label, '张三');
  view.unmount();
});

async function renderRow(view, record) {
  const source = readFileSync(new URL('PackagePage.vue', directory), 'utf8');
  const slot = source.match(/(<template #bodyCell[\s\S]*?)\s*<\/BasicTable>/)[1];
  const app = vue.createSSRApp({ render: vue.compile(`<BasicTable>${slot}</BasicTable>`), setup: () => ({ ...view.state }) });
  app.component('BasicTable', {
    setup:
      (_, { slots }) =>
      () =>
        vue.h('div', slots.bodyCell({ column: { key: 'action' }, record, text: '' })),
  });
  app.component('a-space', {
    setup:
      (_, { slots }) =>
      () =>
        vue.h('div', slots.default?.()),
  });
  app.component('a-image', {});
  app.component('a-tooltip', {});
  app.directive('auth', {});
  return renderToString(app);
}

test('actual row renders management and risk only for managers while recipients retain field-based editing', async () => {
  const view = mount('PackagePage.vue');
  const ownerHtml = await renderRow(view, owner('1'));
  for (const label of ['删除', '转移', '自动拉黑', '修改']) assert.ok(ownerHtml.includes(label), label);
  assert.doesNotMatch(ownerHtml, /编辑|共享管理|共享应用/);
  for (const record of [{ id: '2', canManage: false }, { id: '3' }]) {
    const html = await renderRow(view, record);
    assert.doesNotMatch(html, /删除|转移|共享管理|自动拉黑/);
    assert.match(html, /修改/);
    await view.state.openAuto(record);
    assert.equal(view.state.autoOpen, false);
  }
  view.unmount();
});

test('details exclude canManage metadata even when all field permissions are granted', async () => {
  const view = mount('PackagePage.vue');
  view.state.detail = { id: '1', canManage: true, appName: '应用' };
  assert.equal(
    view.state.detailFields.some((field) => field.key === 'canManage'),
    false
  );
  assert.equal(
    view.state.detailFields.some((field) => field.key === 'appName'),
    true
  );
  view.unmount();
});

test('add and export sit on the left and the toolbar has no batch share', () => {
  const source = readFileSync(new URL('PackagePage.vue', directory), 'utf8');
  const title = source.match(/<template #tableTitle>([\s\S]*?)<\/template>/)[1];
  const toolbar = source.match(/<template #toolbar>([\s\S]*?)<\/template>/)[1];
  const top = source.match(/<template #tableTop>([\s\S]*?)<\/template>/)[1];
  assert.match(title, /新增/);
  assert.match(title, /导出/);
  assert.match(toolbar, /批量删除/);
  assert.doesNotMatch(toolbar, /新增|导出|共享应用/);
  assert.match(top, /已选中/);
  assert.doesNotMatch(source, /未选中任何数据/);
});

test('delete confirmation snapshots IDs, rechecks permission and refresh invalidates selected app capabilities', async () => {
  const calls = [];
  const view = mount('PackagePage.vue', {
    packageApi: () => ({ list: async () => ({ records: [], total: 0 }), remove: async (ids) => calls.push(ids) }),
  });
  const ids = ['1'];
  view.table().rowSelection.onChange(ids, [owner('1')]);
  view.state.confirmRemove(ids);
  ids.push('2');
  await view.confirmations[0].onOk();
  assert.deepEqual(calls, [['1']]);
  view.table().rowSelection.onChange(['1'], [owner('1')]);
  view.state.confirmRemove(['1']);
  view.deny();
  await view.confirmations[1].onOk();
  assert.equal(calls.length, 1);
  await view.table().api({ pageNo: 1, pageSize: 20 });
  assert.equal(view.state.selected.length, 0);
  assert.equal(view.state.selectedRows.length, 0);
  view.unmount();
});

test('sharing capability does not change selection behavior in other resource pages', async () => {
  const view = mount('PackagePage.vue', {}, 'pwa');
  view.table().rowSelection.onChange(['1'], [{ id: '1' }]);
  assert.equal(view.state.canRemoveSelected, true);
  await view.table().api({ pageNo: 2, pageSize: 20 });
  assert.deepEqual(view.state.selected, ['1']);
  view.unmount();
});

test('a failed grant keeps the recipient and a retry can succeed', async () => {
  let calls = 0;
  const view = mount(undefined, {
    transferApps: async (...args) => {
      calls++;
      if (calls === 1) throw new Error('临时失败');
      assert.deepEqual(args, [['9007199254740993'], 'u1']);
      return 1;
    },
  });
  await view.state.open([owner()]);
  view.state.targetUser = 'u1';
  await view.state.grant();
  assert.equal(view.state.saving, false);
  assert.equal(view.state.targetUser, 'u1');
  assert.equal(view.state.visible, true);
  await view.state.grant();
  assert.equal(calls, 2);
  assert.equal(view.emitted.length, 1);
  assert.equal(view.state.visible, false);
  view.unmount();
});
