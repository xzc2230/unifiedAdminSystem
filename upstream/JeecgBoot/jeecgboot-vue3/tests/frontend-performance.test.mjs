import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';
import * as vue from 'vue';
import { parse, compileScript } from '@vue/compiler-sfc';
import { createRouter, createMemoryHistory } from 'vue-router';
import * as routerRuntime from 'vue-router';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = new URL('../', import.meta.url);
const require = createRequire(import.meta.url);
function loadTs(file, extra = {}) {
  const url = new URL(file, root);
  const source = (existsSync(url) ? readFileSync(url, 'utf8') : '')
    .replaceAll('import.meta.env.VITE_ENABLE_AI', JSON.stringify(String(extra['/@/settings/features']?.AI_ENABLED ?? false)));
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const module = { exports: {} };
  new Function('require', 'exports', 'module', compiled)((id) => extra[id] ?? (id === 'vue-router' ? routerRuntime : require(id)), module.exports, module);
  return module.exports;
}
const flush = async () => { await new Promise(setImmediate); await vue.nextTick(); };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

function page(file, extra = {}) {
  const { descriptor } = parse(readFileSync(new URL(file, root), 'utf8'), { filename: file });
  const compiled = compileScript(descriptor, { id: file });
  const code = ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const module = { exports: {} };
  new Function('require', 'exports', 'module', code)((id) => id === 'vue' ? vue : extra[id] ?? {}, module.exports, module);
  return module.exports.default;
}

function makeRenderer() {
  return vue.createRenderer({
    createElement: type => ({ type, children: [], parent: null }),
    createText: text => ({ text, parent: null }), createComment: () => ({ parent: null }),
    insert(child, parent, anchor = null) {
      if (child.parent) { const i = child.parent.children.indexOf(child); if (i >= 0) child.parent.children.splice(i, 1); }
      child.parent = parent;
      const i = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(i < 0 ? parent.children.length : i, 0, child);
    },
    remove(child) { const parent = child.parent; if (parent) parent.children.splice(parent.children.indexOf(child), 1); child.parent = null; },
    parentNode: node => node.parent, nextSibling: () => null,
    setText(node, text) { node.text = text; }, setElementText(node, text) { node.text = text; }, patchProp() {},
  });
}

test('real package wrappers match menu names so A → B → A preserves instance state', async () => {
  const A = page('src/views/packagemanagement/apps/index.vue');
  const B = page('src/views/packagemanagement/request-logs/index.vue');
  assert.equal(A.name || A.__name, 'Packageapps');
  assert.equal(B.name || B.__name, 'Packagerequestlogs');
  let mounted = 0;
  const children = [];
  const Child = { setup() { mounted++; const filter = vue.ref(''); children.push(filter); return () => vue.h('div', filter.value); } };
  A.render = B.render = () => vue.h(Child);
  const active = vue.shallowRef(A), scope = vue.ref('user1:tenant1');
  const app = makeRenderer().createApp({ setup: () => () => vue.h(vue.KeepAlive, { key: scope.value, include: ['Packageapps', 'Packagerequestlogs'], max: 12 }, () => vue.h(active.value)) });
  app.mount({ children: [] });
  children[0].value = 'retained search';
  active.value = B; await flush(); active.value = A; await flush();
  assert.equal(mounted, 2);
  assert.equal(children[0].value, 'retained search');
  scope.value = 'user1:tenant2'; await flush();
  assert.equal(mounted, 3, 'tenant switch must discard previous instances');
  app.unmount();
});

test('all cache migration entries refer to named existing package pages, without changing grants', () => {
  const migration = new URL('../jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/V20261111_06__package_page_cache.sql', root);
  assert.ok(existsSync(migration), 'cache opt-in must have an incremental deployment SQL');
  const sql = readFileSync(migration, 'utf8');
  assert.doesNotMatch(sql, /\b(?:DELETE|INSERT|DROP)\b/i);
  const paths = [...sql.matchAll(/component\s*=\s*'(packagemanagement\/[^']+)'/g)].map(m => m[1]);
  assert.ok(paths.length >= 3);
  for (const component of paths) {
    const compiled = page(`src/views/${component}.vue`);
    assert.notEqual(compiled.name || compiled.__name, 'index', component);
  }
});

test('cache refresh skips initial/young activation, retries failures, and coalesces overlap', async () => {
  const { createPageRefreshPolicy } = loadTs('src/hooks/web/pageRefreshPolicy.ts');
  assert.equal(typeof createPageRefreshPolicy, 'function');
  let now = 1000, requests = 0;
  const gate = deferred();
  const policy = createPageRefreshPolicy(30_000, () => now);
  const refresh = async () => { requests++; await gate.promise; };
  await policy.activate(refresh); assert.equal(requests, 0, 'mount owns first fetch');
  policy.markFresh(); now += 100;
  await policy.activate(refresh); assert.equal(requests, 0);
  now += 30_000;
  const first = policy.activate(refresh), second = policy.activate(refresh);
  assert.equal(requests, 1); gate.resolve(); await Promise.all([first, second]);
  now += 30_001;
  await assert.rejects(policy.activate(async () => { throw new Error('network'); }), /network/);
  await policy.activate(async () => { requests++; });
  assert.equal(requests, 2, 'failed request must not freshen the cache');
});

test('returning before the initial list request finishes never starts a second request', async () => {
  const { createPageRefreshPolicy } = loadTs('src/hooks/web/pageRefreshPolicy.ts');
  const policy = createPageRefreshPolicy(30_000), gate = deferred();
  const initial = policy.trackRequest(() => gate.promise);
  let duplicates = 0;
  await policy.activate(async () => { duplicates++; });
  await policy.activate(async () => { duplicates++; });
  assert.equal(duplicates, 0);
  gate.resolve([]); await initial;
  await policy.activate(async () => { duplicates++; });
  assert.equal(duplicates, 0);
});

test('freshness follows the latest list request even when an older result arrives last', async () => {
  const { createPageRefreshPolicy } = loadTs('src/hooks/web/pageRefreshPolicy.ts');
  const policy = createPageRefreshPolicy(30_000);
  await policy.activate(async () => {});
  const old = deferred();
  const older = policy.trackRequest(() => old.promise);
  await assert.rejects(policy.trackRequest(async () => { throw new Error('latest failed'); }));
  old.resolve([]); await older;
  let retries = 0;
  await policy.activate(async () => { retries++; });
  assert.equal(retries, 1, 'an old success must not mark a failed current query fresh');
});

test('loading finishes immediately, and an old cancelled navigation cannot close the next one', async () => {
  const { setupPageLoadingGuard } = loadTs('src/router/guard/pageLoading.ts');
  assert.equal(typeof setupPageLoadingGuard, 'function');
  const routes = ['/', '/a', '/b', '/bad'].map(path => ({ path, component: { render: () => null } }));
  const router = createRouter({ history: createMemoryHistory(), routes });
  let loading = false;
  setupPageLoadingGuard(router, { shouldLoad: () => true, setLoading: value => { loading = value; } });
  await router.push('/'); assert.equal(loading, false);
  const a = deferred(), b = deferred();
  router.beforeResolve(async to => { if (to.path === '/a') await a.promise; if (to.path === '/b') await b.promise; if (to.path === '/bad') throw new Error('chunk failed'); });
  const old = router.push('/a'); await flush(); assert.equal(loading, true);
  const current = router.push('/b'); await flush();
  a.resolve(); await old; assert.equal(loading, true);
  b.resolve(); await current; assert.equal(loading, false);
  await assert.rejects(router.push('/bad'), /chunk failed/); assert.equal(loading, false);
});

test('disabled AI menus are removed recursively without mutating unrelated permissions', () => {
  const { filterAiMenus } = loadTs('src/settings/featureMenus.ts');
  assert.equal(typeof filterAiMenus, 'function');
  const menus = [{ path: '/root', children: [{ path: '/apps' }, { path: '/ai/chat/abc' }, { path: '/rag', component: '/super/airag/aiapp/AiAppList' }] }];
  const before = structuredClone(menus);
  assert.deepEqual(filterAiMenus(menus, false)[0].children, [{ path: '/apps' }]);
  assert.deepEqual(menus, before);
  assert.deepEqual(filterAiMenus(menus, true), menus);
  assert.deepEqual(filterAiMenus([{ path: '/group', children: [{ path: '/ai' }] }], false), []);
});

test('returning to the current route cancels the loading of a slow pending navigation', async () => {
  const { setupPageLoadingGuard } = loadTs('src/router/guard/pageLoading.ts');
  const router = createRouter({ history: createMemoryHistory(), routes: ['/', '/slow'].map(path => ({ path, component: { render: () => null } })) });
  let loading = false;
  setupPageLoadingGuard(router, { shouldLoad: () => true, setLoading: value => { loading = value; } });
  await router.push('/');
  const gate = deferred();
  router.beforeResolve(async to => { if (to.path === '/slow') await gate.promise; });
  const slow = router.push('/slow'); await flush(); assert.equal(loading, true);
  await router.push('/'); assert.equal(loading, false);
  gate.resolve(); await slow; assert.equal(router.currentRoute.value.path, '/');
});

test('cached packstat refresh updates author choices before reloading the list', async () => {
  let activate, maxAge;
  const calls = [];
  const Component = page('src/views/packagemanagement/components/DevRecordPage.vue', {
    '/@/hooks/web/useCachedPageRefresh': { useCachedPageRefresh(refresh, ttl) { activate = refresh; maxAge = ttl; return {}; } },
    '/@/components/Table': { useTable: () => [() => {}, { reload: async () => { calls.push('rows'); return []; }, setProps() {} }] },
  });
  Component.render = () => null;
  const app = makeRenderer().createApp(Component, { title: '包统计', path: 'packstat', domain: 'packstat', fields: [], filters: [], refreshChoices: async () => { calls.push('authors'); } });
  app.mount({ children: [] });
  await activate();
  assert.deepEqual(calls, ['authors', 'rows']);
  assert.equal(maxAge, 5_000);
  app.unmount();
});

test('a duplicate cancellation cannot hide a newer navigation loading', async () => {
  const { setupPageLoadingGuard } = loadTs('src/router/guard/pageLoading.ts');
  const router = createRouter({ history: createMemoryHistory(), routes: ['/', '/a', '/b'].map(path => ({ path, component: { render: () => null } })) });
  let loading = false;
  setupPageLoadingGuard(router, { shouldLoad: () => true, setLoading: value => { loading = value; } });
  await router.push('/');
  const a = deferred(), b = deferred();
  router.beforeResolve(async to => { if (to.path === '/a') await a.promise; if (to.path === '/b') await b.promise; });
  const old = router.push('/a'); await flush();
  const duplicate = router.push('/'); const next = router.push('/b');
  await duplicate; await flush(); assert.equal(loading, true);
  a.resolve(); await old; assert.equal(loading, true);
  b.resolve(); await next; assert.equal(loading, false);
});

test('AI toggle controls chat and static routes while keeping Online registered', async () => {
  for (const enabled of [false, true]) {
    let online = 0, chat = 0;
    const { registerSuper } = loadTs('src/views/super/registerSuper.ts', {
      '/@/settings/features': { AI_ENABLED: enabled },
      './online/register': { register: async () => { online++; } },
      './airag/aiapp/chat/route/register': { register: async () => { chat++; } },
    });
    await registerSuper({});
    assert.equal(online, 1); assert.equal(chat, Number(enabled));
    const { staticRoutesList } = loadTs('src/router/routes/staticRouter.ts', {
      '/@/settings/features': { AI_ENABLED: enabled }, '/@/router/constant': { LAYOUT: () => null },
    });
    assert.equal(staticRoutesList.length, Number(enabled));
  }
});

test('an older list response cannot overwrite a later filter result or loading state', async () => {
  const lodash = { get: (value, key) => value?.[key], cloneDeep: value => structuredClone(value) };
  const { useDataSource } = loadTs('src/components/Table/src/hooks/useDataSource.ts', {
    vue, 'lodash-es': lodash,
    '/@/hooks/core/useTimeout': { useTimeoutFn() {} },
    '/@/utils/uuid': { buildUUID: () => 'id' },
    '/@/utils/is': { isFunction: value => typeof value === 'function', isBoolean: value => typeof value === 'boolean' },
    '../const': { FETCH_SETTING: { pageField: 'pageNo', sizeField: 'pageSize', listField: 'records', totalField: 'total' }, PAGE_SIZE: 10, ROW_KEY: 'key' },
  });
  const old = deferred(), current = deferred();
  let count = 0, loading, table;
  const events = [];
  const app = makeRenderer().createApp({ setup() {
    table = useDataSource(vue.computed(() => ({ api: () => (++count === 1 ? old.promise : current.promise), pagination: false })), {
      getPaginationInfo: vue.ref(false), setPagination() {}, setLoading: value => { loading = value; },
      validate: () => ({}), clearSelectedRowKeys() {}, tableData: vue.ref([]),
    }, (...args) => events.push(args));
    return () => null;
  } });
  app.mount({ children: [] });
  const one = table.reload(), two = table.reload();
  old.resolve({ records: [{ id: 'old' }], total: 1 }); await one;
  assert.equal(loading, true, 'old request must not close a newer loading');
  current.resolve({ records: [{ id: 'new' }], total: 1 }); await two;
  assert.deepEqual(table.getDataSource(), [{ id: 'new' }]);
  assert.equal(events.filter(e => e[0] === 'fetch-success').length, 1);
  app.unmount();
});

test('production chunk policy leaves shared helpers eager and preserves lazy editor initialization order', async () => {
  const { lazyChunks } = loadTs('build/vite/lazyChunks.ts');
  const viteRequire = createRequire(require.resolve('vite/package.json'));
  const { rolldown } = await import(pathToFileURL(viteRequire.resolve('rolldown')).href);
  const fixture = await mkdtemp(join(tmpdir(), 'jeecg-lazy-chunks-'));
  try {
    await mkdir(join(fixture, 'src/utils'), { recursive: true });
    await mkdir(join(fixture, 'node_modules/tinymce'), { recursive: true });
    const files = {
      'package.json': '{"type":"module"}',
      'helper.js': 'export const shared = value => value + 1;',
      'entry.js': "import { shared } from './helper.js'; globalThis.__jeecgLoad = () => import('./editor.js'); globalThis.__jeecgShared = shared(1);",
      'src/utils/tinymceGlobalShim.ts': "import { shared } from '../../helper.js'; globalThis.__jeecgChunkSmoke = shared(4);",
      'node_modules/tinymce/plugin.js': "if(globalThis.__jeecgChunkSmoke !== 5) throw new Error('shim must run before plugin'); globalThis.__jeecgChunkSmoke++;",
      'editor.js': "import './src/utils/tinymceGlobalShim.ts'; import './node_modules/tinymce/plugin.js'; export const editorReady = globalThis.__jeecgChunkSmoke;",
    };
    for (const [file, source] of Object.entries(files)) await writeFile(join(fixture, file), source);
    const bundle = await rolldown({ input: join(fixture, 'entry.js'), preserveEntrySignatures: false });
    try {
      const { output } = await bundle.write({ dir: join(fixture, 'out'), format: 'es', codeSplitting: lazyChunks, strictExecutionOrder: true, entryFileNames: 'entry.js' });
      const entry = output.find(chunk => chunk.type === 'chunk' && chunk.isEntry);
      assert.ok(entry);
      assert.ok(!entry.imports.some(file => file.includes('tinymce-vendor')));
      await import(pathToFileURL(join(fixture, 'out/entry.js')).href);
      assert.equal(globalThis.__jeecgShared, 2);
      assert.equal(globalThis.__jeecgChunkSmoke, undefined, 'entry must not execute the editor');
      assert.equal((await globalThis.__jeecgLoad()).editorReady, 6);
    } finally { await bundle.close(); }
  } finally {
    delete globalThis.__jeecgChunkSmoke;
    delete globalThis.__jeecgLoad;
    delete globalThis.__jeecgShared;
    await rm(fixture, { recursive: true, force: true });
  }
});
