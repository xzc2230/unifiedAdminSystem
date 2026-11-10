import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

test('sharing API uses explicit JSON bodies, encoded IDs and canonical response contract', async () => {
  const calls = [];
  const http = Object.fromEntries(
    ['get', 'post', 'put', 'delete'].map((method) => [
      method,
      async (args) => {
        calls.push([method, args]);
        return method === 'post' ? 2 : { records: [], total: 0 };
      },
    ])
  );
  const module = { exports: {} };
  const source = readFileSync(new URL('../src/api/packagemanagement/index.ts', import.meta.url), 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  new Function('require', 'module', 'exports', js)(() => ({ defHttp: http }), module, module.exports);
  const api = module.exports;
  assert.equal(typeof api.shareApps, 'function');
  assert.equal(await api.shareApps(['9007199254740993'], 'u1', '协作'), 2);
  assert.equal(await api.revokeAppShares(['9007199254740993'], 'u1', '结束'), 2);
  await api.getAppShares('a/b', { pageNo: 2, pageSize: 20 });
  await api.getAppShareAudit('a/b', { pageNo: 3, pageSize: 20 });
  assert.deepEqual(calls, [
    ['post', { url: '/package-admin/apps/shares', data: { ids: ['9007199254740993'], targetUser: 'u1', reason: '协作' } }],
    ['post', { url: '/package-admin/apps/shares/revoke', data: { ids: ['9007199254740993'], targetUser: 'u1', reason: '结束' } }],
    ['get', { url: '/package-admin/apps/a%2Fb/shares', params: { pageNo: 2, pageSize: 20 } }],
    ['get', { url: '/package-admin/apps/a%2Fb/share-audit', params: { pageNo: 3, pageSize: 20 } }],
  ]);
});
