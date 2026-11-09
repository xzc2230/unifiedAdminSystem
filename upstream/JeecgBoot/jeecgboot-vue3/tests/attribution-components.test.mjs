import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc';
import ts from 'typescript';
import * as vue from 'vue';
import * as helpers from '../src/views/attribution/attribution.form.mjs';

// Execute real SFC setup logic with Vue's renderer and replace only host services.
// No browser, network, or new dependencies are required.
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
const empty = () => ({ applicationName: '应用', channelName: '渠道', fallbackUrl: 'https://example.com/fallback', offers: [] });
const row = (count = 3) => ({ applicationId: 'app', channelId: 'channel', configVersion: '8', offerCount: count, editable: true });
const offers = () =>
  [1, 0, 2].map((weight, index) => ({
    ...helpers.newOffer(index, `card-${index}`),
    bindingId: `binding-${index}`,
    targetUrl: `https://example.com/${index}`,
    weight,
    sortOrder: index,
  }));

function mountSfc(filename, overrides = {}, props = {}) {
  const source = readFileSync(new URL(`../src/views/attribution/${filename}`, import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source);
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: filename });
  const template = compileTemplate({
    source: descriptor.template.content,
    filename,
    id: filename,
    compilerOptions: { bindingMetadata: compiled.bindings },
  });
  assert.deepEqual(template.errors, []);
  const js = ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const api = {
    getCapabilities: async () => ({ countryEnabled: false }),
    getConfiguration: async () => ({
      ...empty(),
      offers: offers(),
      managedBindingIds: offers().map((offer) => offer.bindingId),
      expectedConfigVersion: '8',
    }),
    listApplications: async () => ({ records: [], total: 0 }),
    getOffers: async () => ({ offers: offers(), configVersion: '8' }),
    saveConfiguration: async () => ({}),
    ...overrides,
  };
  let open;
  let closed = 0;
  let permitted = true;
  const module = { exports: {} };
  const deps = {
    vue,
    '/@/components/Modal': {
      BasicModal: {},
      useModal: () => [() => {}, { openModal: () => {} }],
      useModalInner: (callback) => {
        open = callback;
        return [
          () => {},
          {
            closeModal: () => {
              closed++;
            },
          },
        ];
      },
    },
    '/@/components/Table': { BasicTable: {} },
    '/@/hooks/web/usePermission': { usePermission: () => ({ hasPermission: () => permitted }) },
    '/@/hooks/web/useMessage': { useMessage: () => ({ createMessage: { success: () => {} } }) },
    './attribution.api': api,
    './attribution.form.mjs': helpers,
  };
  new Function('require', 'module', 'exports', js)(
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
  const app = renderer.createApp(component, props);
  app.mount({ children: [] });
  return {
    state: app._instance.setupState,
    open: (data = {}) => open(data),
    closed: () => closed,
    deny: () => {
      permitted = false;
    },
    unmount: () => app.unmount(),
  };
}

test('failed saves retain every input and retry identical JSON with the same key', async () => {
  const requests = [];
  const view = mountSfc('AttributionModal.vue', {
    saveConfiguration: async (_row, payload, key) => {
      requests.push({ payload, key });
      throw new Error('网络失败');
    },
  });
  view.open();
  Object.assign(view.state.form, empty());
  await view.state.save();
  await view.state.save();
  assert.equal(view.closed(), 0);
  assert.equal(view.state.form.applicationName, '应用');
  assert.equal(requests[0].key, requests[1].key);
  assert.deepEqual(requests[0].payload, requests[1].payload);
  view.state.form.applicationName = '应用修改';
  await view.state.save();
  assert.notEqual(requests[2].key, requests[1].key);
  view.unmount();
});
test('version conflicts retain original expected version, removals, and expanded conditions', async () => {
  const requests = [];
  const view = mountSfc('AttributionModal.vue', {
    saveConfiguration: async (_row, payload) => {
      requests.push(payload);
      throw { code: 409, message: '已有更新', fieldErrors: [{ clientKey: 'card-0', path: 'targetUrl', message: '域名已下架' }] };
    },
  });
  view.open({ row: row() });
  await flush();
  view.state.removeOffer(1);
  view.state.form.applicationName = '保留输入';
  await view.state.save();
  assert.equal(view.state.form.applicationName, '保留输入');
  assert.equal(view.state.form.expectedConfigVersion, '8');
  assert.deepEqual(requests[0].removedBindingIds, ['binding-1']);
  assert.equal(view.state.expanded['card-0'], true);
  assert.match(view.state.saveError, /配置冲突/);
  assert.equal(view.closed(), 0);
  view.unmount();
});
test('read failures block saving, remain retryable, and never expose an empty edit form', async () => {
  let count = 0;
  const view = mountSfc('AttributionModal.vue', {
    getConfiguration: async () => {
      if (!count++) throw new Error('读取失败');
      return { ...empty(), expectedConfigVersion: '8', managedBindingIds: [], offers: [] };
    },
  });
  view.open({ row: row() });
  await flush();
  assert.equal(view.state.ready, false);
  assert.match(view.state.loadError, /读取失败/);
  await view.state.loadConfiguration();
  assert.equal(view.state.ready, true);
  view.unmount();
});
test('cancel closes directly and a late edit response cannot replace the next form', async () => {
  let resolve;
  const view = mountSfc('AttributionModal.vue', {
    getConfiguration: () =>
      new Promise((done) => {
        resolve = done;
      }),
  });
  view.open({ row: row() });
  view.state.cancel();
  assert.equal(view.closed(), 1);
  view.open();
  view.state.form.applicationName = '新的输入';
  resolve({ ...empty(), expectedConfigVersion: '8', managedBindingIds: [] });
  await flush();
  assert.equal(view.state.form.applicationName, '新的输入');
  view.unmount();
});
test('duplicate save clicks issue one request; read-only permissions issue none', async () => {
  let count = 0;
  let resolve;
  const view = mountSfc('AttributionModal.vue', {
    saveConfiguration: () => {
      count++;
      return new Promise((done) => {
        resolve = done;
      });
    },
  });
  view.open();
  Object.assign(view.state.form, empty());
  const pending = view.state.save();
  await view.state.save();
  assert.equal(count, 1);
  resolve({});
  await pending;
  view.open();
  Object.assign(view.state.form, empty());
  view.deny();
  await view.state.save();
  assert.equal(count, 1);
  view.unmount();
});
test('list failures expose retry; search and pagination send server query parameters', async () => {
  const calls = [];
  let fail = true;
  const view = mountSfc('index.vue', {
    listApplications: async (params) => {
      calls.push(params);
      if (fail) throw new Error('列表失败');
      return { records: [row()], total: 60 };
    },
  });
  await flush();
  assert.match(view.state.listError, /列表失败/);
  fail = false;
  view.state.query = ' 应用 ';
  view.state.search();
  await flush();
  assert.deepEqual(calls.at(-1), { q: '应用', pageNo: 1, pageSize: 20 });
  view.state.changePage({ current: 2, pageSize: 20 });
  await flush();
  assert.equal(calls.at(-1).pageNo, 2);
  assert.equal(view.state.rows.length, 1);
  view.unmount();
});
test('only multiple Offers expand; zero weights count, failures retry, and version mismatches do not display stale data', async () => {
  let count = 0;
  let mode = 'fail';
  const view = mountSfc('index.vue', {
    getOffers: async () => {
      count++;
      if (mode === 'fail') throw new Error('展开失败');
      return { offers: offers(), configVersion: mode === 'conflict' ? '9' : '8' };
    },
  });
  await flush();
  view.state.toggleOffers(row(0));
  view.state.toggleOffers(row(1));
  assert.equal(count, 0);
  const record = row();
  view.state.rows = [record];
  view.state.toggleOffers(record);
  await flush();
  const key = view.state.rowKey(record);
  assert.match(view.state.details[key].error, /展开失败/);
  mode = 'ok';
  await view.state.loadOffers(record);
  assert.equal(view.state.details[key].offers.length, 3);
  assert.equal(view.state.details[key].offers[1].weight, 0);
  mode = 'conflict';
  await view.state.loadOffers(record);
  assert.equal(view.state.details[key].conflict, true);
  assert.equal(view.state.details[key].offers.length, 0);
  view.unmount();
});
test('condition editor updates only its own Offer and all Vue templates compile', () => {
  const first = offers()[0];
  const second = offers()[1];
  let updated;
  const view = mountSfc(
    'OfferConditions.vue',
    {},
    {
      offer: first,
      countryEnabled: false,
      errors: [],
      path: 'offers[0]',
      'onUpdate:offer': (value) => {
        updated = value;
      },
    }
  );
  view.state.add();
  assert.equal(updated.conditions.length, 1);
  assert.deepEqual(first.conditions, []);
  assert.deepEqual(second.conditions, []);
  assert.equal(view.state.fieldOptions({ field: 'OS' }).length, 1);
  view.unmount();
  const url = mountSfc('UrlText.vue', {}, { value: 'https://example.com' });
  url.unmount();
});

test('condition changes are blocked while a save is in flight', () => {
  let changes = 0;
  const view = mountSfc(
    'OfferConditions.vue',
    {},
    {
      offer: { ...offers()[0], conditions: [{ field: 'OS', operator: 'IS', values: ['IOS'] }] },
      countryEnabled: true,
      disabled: true,
      errors: [],
      path: 'offers[0]',
      'onUpdate:offer': () => {
        changes++;
      },
    }
  );
  view.state.add();
  view.state.changeMode('OR');
  view.state.patch(0, { values: ['WEB'] });
  view.state.remove(0);
  assert.equal(changes, 0);
  view.unmount();
});

test('editing a payload and reverting it before retry still renews the idempotency key', async () => {
  const keys = [];
  const view = mountSfc('AttributionModal.vue', {
    saveConfiguration: async (_row, _payload, key) => {
      keys.push(key);
      throw new Error('网络失败');
    },
  });
  view.open();
  Object.assign(view.state.form, empty());
  await view.state.save();
  view.state.form.applicationName = '暂时修改';
  view.state.form.applicationName = '应用';
  await view.state.save();
  assert.notEqual(keys[0], keys[1]);
  view.unmount();
});

test('refresh blocks keyboard-triggered expansion of retained old rows', async () => {
  let resolveList;
  let refreshing = false;
  let detailCalls = 0;
  const old = row();
  const view = mountSfc('index.vue', {
    listApplications: () =>
      refreshing
        ? new Promise((resolve) => {
            resolveList = resolve;
          })
        : Promise.resolve({ records: [old], total: 1 }),
    getOffers: async () => {
      detailCalls++;
      return { offers: offers(), configVersion: '8' };
    },
  });
  await flush();
  refreshing = true;
  const pending = view.state.loadList();
  assert.equal(view.state.loading, true);
  view.state.toggleOffers(old);
  await view.state.loadOffers(old);
  assert.equal(detailCalls, 0);
  assert.deepEqual(view.state.expandedKeys, []);
  resolveList({ records: [{ ...old, configVersion: '9' }], total: 1 });
  await pending;
  assert.deepEqual(view.state.details, {});
  view.unmount();
});

test('late details cannot commit when the current row key/version no longer matches', async () => {
  for (const currentRows of [[{ ...row(), configVersion: '9' }], []]) {
    let resolveDetail;
    const old = row();
    const view = mountSfc('index.vue', {
      listApplications: async () => ({ records: [old], total: 1 }),
      getOffers: () =>
        new Promise((resolve) => {
          resolveDetail = resolve;
        }),
    });
    await flush();
    const pending = view.state.loadOffers(old);
    // Simulate replacement after request dispatch without relying on generation changes.
    view.state.rows = currentRows;
    resolveDetail({ offers: offers(), configVersion: old.configVersion });
    await pending;
    assert.equal(view.state.details[view.state.rowKey(old)]?.offers.length ?? 0, 0);
    view.unmount();
  }
});

test('late capabilities from a cancelled modal cannot enable COUNTRY in a later session', async () => {
  const replies = [];
  const view = mountSfc('AttributionModal.vue', {
    getCapabilities: () =>
      new Promise((resolve) => {
        replies.push(resolve);
      }),
  });
  view.open();
  view.state.cancel();
  view.open();
  replies[1]({ countryEnabled: false });
  await flush();
  replies[0]({ countryEnabled: true });
  await flush();
  assert.equal(view.state.countryEnabled, false);
  view.unmount();
});
