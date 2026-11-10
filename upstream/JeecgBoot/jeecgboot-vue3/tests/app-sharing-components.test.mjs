import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import { renderToString } from 'vue/server-renderer';
import { Table } from 'ant-design-vue';
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
const auditRecord = (overrides = {}) => ({
  id: '9007199254740995',
  appId: owner().id,
  granteeUserId: 'u1',
  displayName: '张三',
  operatorUserId: 'admin',
  operatorName: '管理员',
  action: 'GRANT',
  reason: '协作',
  createdAt: '2026-11-10 12:00:00',
  ...overrides,
});

test('actual modal AntD tables locally slice full server collections through independent page-change handlers', async () => {
  const records = Array.from({ length: 45 }, (_, index) => ({
    userId: `user-${index + 1}`,
    displayName: `Recipient-${String(index + 1).padStart(2, '0')}`,
    grantedBy: 'admin',
    reason: 'Collaboration',
    createdAt: '2026-11-10 12:00:00',
  }));
  const auditRecords = records.map((row, index) =>
    auditRecord({
      id: String(9007199254740993n + BigInt(index)),
      granteeUserId: row.userId,
      displayName: row.displayName,
    })
  );
  const requests = [];
  const fullCollection = (collection) => async (id, params) => {
    requests.push({ id, ...params });
    // The current server deliberately ignores pagination and always returns all records.
    return { records: collection, total: collection.length };
  };
  const view = mount(undefined, { getAppShares: fullCollection(records), getAppShareAudit: fullCollection(auditRecords) });
  try {
    await view.state.open([owner()]);
    const source = readFileSync(new URL('AppSharingModal.vue', directory), 'utf8');
    const tables = [...source.matchAll(/<a-table\b[\s\S]*?<\/a-table>/g)].map((match) => match[0]);
    assert.equal(tables.length, 2);
    const renderTables = async () => {
      const handlers = [];
      const app = vue.createSSRApp({ render: vue.compile(`<div>${tables.join('')}</div>`), setup: () => ({ ...view.state }) });
      // Forward actual production template props/slots into the real AntD table, retaining its emitted change handler.
      app.component('a-table', {
        inheritAttrs: false,
        setup: (_, { attrs, slots }) => {
          handlers.push(attrs.onChange);
          return () => vue.h(Table, attrs, slots);
        },
      });
      app.directive('auth', {});
      const html = await renderToString(app);
      const tableBodies = [...html.matchAll(/<tbody\b[\s\S]*?<\/tbody>/g)].map((match) => match[0]);
      const bodies = tableBodies.map((body) => [...body.matchAll(/Recipient-\d{2}/g)].map((row) => row[0]));
      assert.equal(bodies.length, 2);
      const rowKeys = tableBodies.map((body) => [...body.matchAll(/<tr\b[^>]*data-row-key="([^"]+)"/g)].map((row) => row[1]));
      const pageRecords = (collection, page) => collection.slice((page - 1) * 20, page * 20);
      assert.deepEqual(
        rowKeys[0],
        pageRecords(records, view.state.sharesPage).map((row) => row.userId)
      );
      assert.deepEqual(
        rowKeys[1],
        pageRecords(auditRecords, view.state.auditPage).map((row) => row.id),
        'audit row keys use exact backend string IDs without a synthetic key field'
      );
      return { bodies, handlers };
    };
    const expected = (page) => records.slice((page - 1) * 20, page * 20).map((row) => row.displayName);
    let rendered = await renderTables();
    assert.deepEqual(rendered.bodies, [expected(1), expected(1)]);
    for (const page of [2, 3, 1]) {
      await rendered.handlers[0]({ current: page });
      rendered = await renderTables();
      assert.deepEqual(rendered.bodies, [expected(page), expected(1)], 'recipient pages do not alter audit pages');
    }
    for (const page of [2, 3, 1]) {
      await rendered.handlers[1]({ current: page });
      rendered = await renderTables();
      assert.deepEqual(rendered.bodies, [expected(1), expected(page)], 'audit pages do not alter recipient pages');
    }
    assert.equal(view.state.shares.length, 45);
    assert.equal(view.state.audit.length, 45);
    assert.equal(requests.length, 8);
  } finally {
    view.unmount();
  }
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
    '/@/api/packagemanagement/dev': {},
    '../package-dev.mjs': dev,
    '../package-regions.mjs': regions,
    '../package-conversions.mjs': conversions,
    'ant-design-vue': { message: { success() {}, warning() {}, error() {} }, Modal: { confirm: (config) => confirmations.push(config) } },
    '/@/hooks/web/useCopyToClipboard': {},
    '/@/utils/dict/JDictSelectUtil': { initDictOptions: async () => [] },
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
  assert.equal(view.state.canShareSelected, false);
  assert.equal(view.state.canRemoveSelected, false);
  view.state.confirmRemove(['1', '2']);
  assert.equal(view.confirmations.length, 0);
  view.table().rowSelection.onChange(['1'], [owner('1')]);
  assert.equal(view.state.canShareSelected, true);
  assert.equal(view.state.canRemoveSelected, true);
  assert.equal(view.state.canManageRow({ id: '2' }), false);
  assert.equal(view.state.canManageRow({ id: '2', canManage: 'true' }), false);
  view.deny();
  assert.equal(view.state.canShareSelected, false);
  view.state.confirmRemove(['1']);
  assert.equal(view.confirmations.length, 0);
  view.unmount();
});

test('modal validates reason, target and batch; preserves string IDs and sends a snapshot', async () => {
  const calls = [];
  const view = mount(undefined, {
    shareApps: async (...args) => {
      calls.push(args);
      return 1;
    },
  });
  const rows = [owner()];
  await view.state.open(rows);
  assert.equal(view.state.targets[0].label, '张三');
  view.state.targetUser = 'u1';
  for (const reason of ['', '  ', 'x'.repeat(501)]) {
    view.state.reason = reason;
    await view.state.grant();
  }
  assert.equal(calls.length, 0);
  view.state.reason = '  合作维护  ';
  rows.push(owner('2'));
  await view.state.grant();
  assert.deepEqual(calls, [[['9007199254740993'], 'u1', '合作维护']]);
  assert.equal(view.emitted.length, 1);
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
    shareApps: () => {
      calls++;
      return pending.promise;
    },
  });
  await view.state.open([owner()]);
  view.state.targetUser = 'u1';
  view.state.reason = '协作';
  const request = view.state.grant();
  await view.state.grant();
  assert.equal(calls, 1);
  assert.equal(view.state.saving, true);
  pending.reject(new Error('共享失败'));
  await request;
  assert.equal(view.state.targetUser, 'u1');
  assert.equal(view.state.reason, '协作');
  assert.match(view.state.submitError, /共享失败/);
  assert.equal(view.state.saving, false);
  assert.equal(view.state.visible, true);
  view.deny();
  await view.state.grant();
  assert.equal(calls, 1);
  view.unmount();
});

test('single app loads recipients and canonical audit; revoke validates and deduplicates', async () => {
  const calls = [],
    pending = deferred();
  const audit = auditRecord();
  const view = mount(undefined, {
    getAppShares: async () => ({ records: [{ userId: 'u1', displayName: '张三' }], total: 1 }),
    getAppShareAudit: async () => ({ records: [audit], total: 1 }),
    revokeAppShares: (...args) => {
      calls.push(args);
      return pending.promise;
    },
  });
  await view.state.open([owner()]);
  assert.equal(view.state.shares[0].userId, 'u1');
  assert.deepEqual(view.state.audit[0], audit);
  view.state.beginRevoke(view.state.shares[0]);
  for (const reason of ['', ' ', 'x'.repeat(501)]) {
    view.state.revokeReason = reason;
    await view.state.revoke();
  }
  assert.equal(calls.length, 0);
  view.state.revokeReason = '结束协作';
  const request = view.state.revoke();
  await view.state.revoke();
  await view.state.grant();
  assert.deepEqual(calls, [[['9007199254740993'], 'u1', '结束协作']]);
  pending.reject(new Error('撤销失败'));
  await request;
  assert.equal(view.state.revokeReason, '结束协作');
  assert.equal(view.state.revokeTarget.userId, 'u1');
  assert.equal(view.state.saving, false);
  view.unmount();
});

test('successful revoke refreshes recipients/audit and never closes manager', async () => {
  let revoked = false;
  const view = mount(undefined, {
    getAppShares: async () => ({ records: revoked ? [] : [{ userId: 'u1' }], total: revoked ? 0 : 1 }),
    revokeAppShares: async () => {
      revoked = true;
      return 1;
    },
  });
  await view.state.open([owner()]);
  view.state.beginRevoke(view.state.shares[0]);
  view.state.revokeReason = '离开团队';
  await view.state.revoke();
  assert.deepEqual(view.state.shares, []);
  assert.equal(view.state.visible, true);
  assert.equal(view.state.revokeTarget, null);
  assert.equal(view.emitted.length, 1);
  view.unmount();
});

test('closed session target/list/write success or failure cannot pollute newer modal or its lock', async () => {
  for (const fail of [false, true]) {
    const oldTargets = deferred(),
      oldList = deferred(),
      oldWrite = deferred(),
      newWrite = deferred();
    let targetCalls = 0,
      writes = 0;
    const view = mount(undefined, {
      transferTargets: () => (++targetCalls === 1 ? oldTargets.promise : Promise.resolve([{ id: 'u2', displayName: '新接收人' }])),
      getAppShares: (id) => (id === '1' ? oldList.promise : Promise.resolve({ records: [{ userId: 'u2' }], total: 1 })),
      shareApps: () => (++writes === 1 ? oldWrite.promise : newWrite.promise),
    });
    const oldOpen = view.state.open([owner('1')]);
    view.state.targetUser = 'u1';
    view.state.reason = '旧原因';
    const request = view.state.grant();
    view.state.close();
    await view.state.open([owner('2')]);
    view.state.targetUser = 'u2';
    view.state.reason = '新原因';
    const newer = view.state.grant();
    if (fail) {
      oldTargets.reject(new Error('旧目标失败'));
      oldList.reject(new Error('旧列表失败'));
      oldWrite.reject(new Error('旧写入失败'));
    } else {
      oldTargets.resolve([{ id: 'old' }]);
      oldList.resolve({ records: [{ userId: 'old' }], total: 1 });
      oldWrite.resolve(1);
    }
    await oldOpen;
    await request;
    await flush();
    assert.equal(view.state.visible, true);
    assert.equal(view.state.targets[0].value, 'u2');
    assert.equal(view.state.shares[0].userId, 'u2');
    assert.equal(view.state.reason, '新原因');
    assert.equal(view.state.submitError, '');
    assert.equal(view.state.saving, true);
    assert.equal(view.emitted.length, 0);
    newWrite.resolve(1);
    await newer;
    assert.equal(view.emitted.length, 1);
    view.unmount();
  }
});

test('list errors remain visible and retryable with independent shares and audit pagination', async () => {
  const calls = [];
  let fail = true;
  const view = mount(undefined, {
    getAppShares: async (id, params) => {
      calls.push(['shares', id, params]);
      if (fail) throw new Error('列表失败');
      return { records: [], total: 45 };
    },
    getAppShareAudit: async (id, params) => {
      calls.push(['audit', id, params]);
      return { records: [], total: 70 };
    },
  });
  await view.state.open([owner()]);
  assert.match(view.state.sharesError, /列表失败/);
  fail = false;
  await view.state.loadShares(2);
  await view.state.loadAudit(3);
  assert.equal(view.state.sharesError, '');
  assert.equal(view.state.sharesTotal, 45);
  assert.equal(view.state.auditTotal, 70);
  assert.deepEqual(calls.at(-2), ['shares', '9007199254740993', { pageNo: 2, pageSize: 20 }]);
  assert.deepEqual(calls.at(-1), ['audit', '9007199254740993', { pageNo: 3, pageSize: 20 }]);
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
  for (const label of ['删除', '共享管理', '自动拉黑', '编辑']) assert.ok(ownerHtml.includes(label), label);
  for (const record of [{ id: '2', canManage: false }, { id: '3' }]) {
    const html = await renderRow(view, record);
    assert.doesNotMatch(html, /删除|共享管理|自动拉黑/);
    assert.match(html, /编辑/);
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

test('latest same-session shares and audit pages win over late success and failure', async () => {
  for (const fail of [false, true]) {
    const oldShares = deferred(),
      oldAudit = deferred(),
      newShares = deferred(),
      newAudit = deferred();
    const view = mount(undefined, {
      getAppShares: (_id, params) =>
        params.pageNo === 2 ? oldShares.promise : params.pageNo === 3 ? newShares.promise : Promise.resolve({ records: [], total: 0 }),
      getAppShareAudit: (_id, params) =>
        params.pageNo === 2 ? oldAudit.promise : params.pageNo === 3 ? newAudit.promise : Promise.resolve({ records: [], total: 0 }),
    });
    await view.state.open([owner()]);
    const firstShares = view.state.loadShares(2),
      firstAudit = view.state.loadAudit(2);
    const lastShares = view.state.loadShares(3),
      lastAudit = view.state.loadAudit(3);
    if (fail) {
      oldShares.reject(new Error('旧页失败'));
      oldAudit.reject(new Error('旧页失败'));
    } else {
      oldShares.resolve({ records: [{ userId: 'old' }], total: 1 });
      oldAudit.resolve({ records: [auditRecord({ granteeUserId: 'old' })], total: 1 });
    }
    await Promise.all([firstShares, firstAudit]);
    assert.equal(view.state.sharesLoading, true);
    assert.equal(view.state.auditLoading, true);
    newShares.resolve({ records: [{ userId: 'new' }], total: 41 });
    newAudit.resolve({ records: [auditRecord({ id: '9007199254740996', granteeUserId: 'new' })], total: 41 });
    await Promise.all([lastShares, lastAudit]);
    assert.equal(view.state.shares[0].userId, 'new');
    assert.equal(view.state.audit[0].granteeUserId, 'new');
    assert.equal(view.state.sharesError, '');
    assert.equal(view.state.auditError, '');
    view.unmount();
  }
});

test('actual toolbar disables mixed, unknown, over-limit and role-denied sharing selections', async () => {
  const view = mount('PackagePage.vue');
  const source = readFileSync(new URL('PackagePage.vue', directory), 'utf8');
  const toolbar = source.match(/<template #toolbar>([\s\S]*?)<\/template>/)[1];
  const render = async () => {
    const app = vue.createSSRApp({ render: vue.compile(`<div>${toolbar}</div>`), setup: () => ({ ...view.state }) });
    app.component('a-button', {
      setup:
        (_, { slots, attrs }) =>
        () =>
          vue.h('button', attrs, slots.default?.()),
    });
    app.directive('auth', {});
    return renderToString(app);
  };
  view.table().rowSelection.onChange(['1'], [owner('1')]);
  assert.match(await render(), /<button(?![^>]*disabled)[^>]*>共享应用<\/button>/);
  for (const rows of [[owner('1'), { id: '2', canManage: false }], [{ id: '1' }], Array.from({ length: 101 }, (_, i) => owner(String(i + 1)))]) {
    view.table().rowSelection.onChange(
      rows.map((row) => row.id),
      rows
    );
    assert.match(await render(), /<button[^>]*disabled[^>]*>共享应用<\/button>/);
  }
  view.table().rowSelection.onChange(['1'], [owner('1')]);
  view.deny();
  assert.match(await render(), /<button[^>]*disabled[^>]*>共享应用<\/button>/);
  view.unmount();
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

test('500-character reasons are accepted and a failed grant unlocks a successful retry', async () => {
  let calls = 0;
  const view = mount(undefined, {
    shareApps: async () => {
      if (++calls === 1) throw new Error('临时失败');
      return 1;
    },
  });
  await view.state.open([owner()]);
  view.state.targetUser = 'u1';
  view.state.reason = '共'.repeat(500);
  await view.state.grant();
  assert.equal(view.state.reason.length, 500);
  assert.equal(view.state.saving, false);
  await view.state.grant();
  assert.equal(calls, 2);
  assert.equal(view.emitted.length, 1);
  assert.equal(view.state.reason, '');
  view.unmount();
});
