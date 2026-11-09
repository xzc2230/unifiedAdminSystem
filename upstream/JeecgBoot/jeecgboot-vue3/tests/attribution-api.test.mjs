import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

function loadApi(respond) {
  const requests = [];
  const module = { exports: {} };
  const source = readFileSync(new URL('../src/views/attribution/attribution.api.ts', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const http = {
    request: async (config, options) => {
      requests.push({ config, options });
      return respond(config);
    },
  };
  new Function('require', 'module', 'exports', compiled)(() => ({ defHttp: http }), module, module.exports);
  return { api: module.exports, requests };
}
const row = { applicationId: '9007199254740993', channelId: 'channel/a', configVersion: '7' };

test('JEECG success codes unwrap result and preserve records/total', async () => {
  for (const code of [0, 200]) {
    const result = { records: [], total: 0 };
    const { api, requests } = loadApi(() => ({ code, success: true, result }));
    assert.equal(await api.listApplications({ q: 'app', pageNo: 2, pageSize: 20 }), result);
    assert.equal(requests[0].config.url, '/attribution-admin/applications');
    assert.deepEqual(requests[0].config.params, { q: 'app', pageNo: 2, pageSize: 20 });
    assert.equal(requests[0].options.isTransformResponse, false);
  }
});
test('expand and edit use exact paths, string IDs, and expansion version', async () => {
  const { api, requests } = loadApi(() => ({ code: 200, result: {} }));
  await api.getOffers(row);
  await api.getConfiguration(row);
  await api.getCapabilities();
  assert.equal(requests[0].config.url, '/attribution-admin/applications/9007199254740993/channels/channel%2Fa/offers');
  assert.deepEqual(requests[0].config.params, { configVersion: '7' });
  assert.equal(requests[1].config.url, '/attribution-admin/application-configurations/9007199254740993/channels/channel%2Fa');
  assert.equal(requests[2].config.url, '/attribution-admin/capabilities');
});
test('create and edit send JSON and Idempotency-Key without codes or tenant fields', async () => {
  const payload = { applicationName: '应用', channelName: '渠道', fallbackUrl: 'https://example.com', offers: [] };
  const { api, requests } = loadApi(() => ({ code: 200, result: {} }));
  await api.saveConfiguration(undefined, payload, 'same-key');
  await api.saveConfiguration(row, { ...payload, expectedConfigVersion: '7', removedBindingIds: ['b1'] }, 'edit-key');
  assert.equal(requests[0].config.method, 'POST');
  assert.equal(requests[0].config.url, '/attribution-admin/application-configurations');
  assert.equal(requests[0].config.headers['Idempotency-Key'], 'same-key');
  assert.deepEqual(requests[0].config.data, payload);
  assert.equal(requests[1].config.method, 'PUT');
  assert.equal(requests[1].config.data.expectedConfigVersion, '7');
  assert.deepEqual(requests[1].config.data.removedBindingIds, ['b1']);
});
test('business failures preserve the complete fieldErrors object', async () => {
  const failure = { code: 400, success: false, message: '字段错误', fieldErrors: [{ path: 'fallbackUrl', message: '域名未获准' }] };
  const { api } = loadApi(() => failure);
  await assert.rejects(api.saveConfiguration(undefined, {}, 'key'), (error) => error === failure);
});
