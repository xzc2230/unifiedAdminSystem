import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';

const root = new URL('../', import.meta.url);
const require = createRequire(new URL('package.json', root));
const ts = require('typescript');
const source = new URL('src/views/system/businessIdentity/identity.behavior.ts', root);
const js = ts.transpileModule(existsSync(source) ? readFileSync(source, 'utf8') : '', {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
}).outputText;
const behavior = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`);

test('legacy IDs remain decimal strings through the full positive Long range', () => {
  assert.equal(typeof behavior.validateLegacyId, 'function');
  for (const id of ['1', '1000000000000', '9007199254740993', '9223372036854775807']) {
    assert.equal(behavior.validateLegacyId(id), '');
  }
  for (const id of ['', '0', '-1', '1.1', '1e3', ' 1', '01', '9223372036854775808', 123]) {
    assert.ok(behavior.validateLegacyId(id), String(id));
  }
});

test('reason rejects blank and oversized input and accepts the boundary', () => {
  assert.equal(typeof behavior.validateReason, 'function');
  for (const reason of ['', ' \n ', 'a'.repeat(501), ` ${'a'.repeat(500)}`]) assert.ok(behavior.validateReason(reason));
  assert.equal(behavior.validateReason('a'.repeat(500)), '');
  assert.equal(behavior.validateReason(' 人工核实 '), '');
});

const target = { tenantId: 3, userId: 'user-1', legacyUserId: '9007199254740993' };
const available = { legacyUserId: target.legacyUserId, available: true, conflict: null, resourceCounts: [], ownershipConflicts: 0 };

test('preview is accepted only for the exact tenant, user and string ID', () => {
  assert.equal(typeof behavior.createPreviewGuard, 'function');
  const guard = behavior.createPreviewGuard();
  const request = guard.begin(target);
  assert.equal(guard.accept(request, available), true);
  assert.equal(guard.canBind(target), true);
  for (const change of [{ tenantId: 4 }, { userId: 'user-2' }, { legacyUserId: '1' }]) {
    assert.equal(guard.canBind({ ...target, ...change }), false);
  }
});

test('editing invalidates a completed preview even if the input is restored', () => {
  const guard = behavior.createPreviewGuard();
  guard.accept(guard.begin(target), available);
  guard.invalidate();
  assert.equal(guard.canBind(target), false);
});

test('late previews cannot replace a newer preview or survive close and reopen', () => {
  const guard = behavior.createPreviewGuard();
  const old = guard.begin(target);
  const current = guard.begin(target);
  assert.equal(guard.accept(old, available), false);
  assert.equal(guard.accept(current, available), true);
  guard.invalidate();
  assert.equal(guard.accept(current, available), false);
});

test('conflicting, unavailable or mismatched previews cannot authorize binding', () => {
  for (const change of [{ available: false }, { conflict: '已被占用' }, { ownershipConflicts: 1 }, { legacyUserId: '1' }, { available: 'true' }]) {
    const guard = behavior.createPreviewGuard();
    guard.accept(guard.begin(target), { ...available, ...change });
    assert.equal(guard.canBind(target), false);
  }
});

test('request gate ignores stale success and failure after replacement or disposal', () => {
  assert.equal(typeof behavior.createRequestGate, 'function');
  const gate = behavior.createRequestGate();
  const first = gate.next();
  const second = gate.next();
  assert.equal(gate.isCurrent(first), false);
  assert.equal(gate.isCurrent(second), true);
  gate.invalidate();
  assert.equal(gate.isCurrent(second), false);
});

test('submission lock prevents duplicate writes until the first settles', () => {
  assert.equal(typeof behavior.createSubmissionLock, 'function');
  const lock = behavior.createSubmissionLock();
  assert.equal(lock.acquire(), true);
  assert.equal(lock.acquire(), false);
  lock.release();
  assert.equal(lock.acquire(), true);
});

test('HTTP errors show backend Result.message and never stringify arbitrary objects', () => {
  const error = Object.assign(new Error('Request failed with status code 409'), { response: { data: { message: '历史账号已被其他用户占用' } } });
  assert.equal(behavior.errorText(error, '操作失败'), '历史账号已被其他用户占用');
  assert.equal(behavior.errorText({ response: { data: { message: { private: 'object' } } } }, '操作失败'), '操作失败');
  assert.equal(behavior.errorText({ message: '业务校验失败' }, '操作失败'), '业务校验失败');
});
