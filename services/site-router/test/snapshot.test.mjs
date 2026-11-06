import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { SnapshotStore, validateEnvelope, matchRequest, requestPath } from '../src/snapshot.mjs';
import { envelope } from './fixtures.mjs';

test('rejects altered payload, future schema, duplicate priorities, unknown rule domains and target queries', () => {
  assert.throws(() => validateEnvelope({ ...envelope(), payload: envelope().payload + ' ' }));
  assert.throws(() => validateEnvelope(envelope({ schemaVersion: 2 })));
  const original = JSON.parse(envelope().payload);
  assert.throws(() => validateEnvelope(envelope({ rules: [...original.rules, { ...original.rules[0], id: 'other' }] })));
  assert.throws(() => validateEnvelope(envelope({ rules: [{ ...original.rules[0], hostname: 'foreign.example.com' }] })));
  assert.throws(() => validateEnvelope(envelope({ defaultTarget: { id: 'main', url: 'https://origin.example.com?x=y' } })));
});

test('exact host scope, segment prefix and priority match do not leak to another domain', () => {
  const snapshot = validateEnvelope(envelope());
  assert.equal(matchRequest([snapshot], 'WWW.EXAMPLE.COM:443', '/api/items?x=1').target.id, 'api-target');
  assert.equal(matchRequest([snapshot], 'www.example.com', '/api').target.id, 'api-target');
  assert.equal(matchRequest([snapshot], 'www.example.com', '/apian').target.id, 'main');
  assert.equal(matchRequest([snapshot], 'www.example.com.evil.org', '/api'), null);
  assert.throws(() => matchRequest([snapshot], 'www.example.com/evil', '/api'));
});

test('ambiguous encoded separators and dot segments are rejected instead of matched differently by upstream', () => {
  for (const path of ['/api/../admin', '/%2e%2e/x', '/api%2fadmin', '/api%5cadmin', '//evil', '/x\\y', 'http://evil/x', '/x%zz', '/x#fragment']) {
    assert.throws(() => requestPath(path), path);
  }
  assert.equal(requestPath('/api?next=%2F'), '/api');
});

test('atomic full-set persist and restore, invalid updates retain the good set, empty set revokes domains', async (t) => {
  const dir = await mkdtemp(join(tmpdir(), 'sp-router-test-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const file = join(dir, 'snapshot.json');
  const store = new SnapshotStore(file, 'node-a');
  await store.apply({ nodeId: 'node-a', snapshots: [envelope()] });
  assert.equal(store.snapshots.length, 1);
  const before = await readFile(file, 'utf8');
  await assert.rejects(store.apply({ nodeId: 'node-b', snapshots: [] }));
  await assert.rejects(store.apply({ nodeId: 'node-a', snapshots: [envelope(), envelope({ siteId: 'other' })] }));
  assert.equal(await readFile(file, 'utf8'), before);
  assert.equal(store.snapshots[0].siteId, 'site-a');
  const restarted = new SnapshotStore(file, 'node-a');
  assert.equal(await restarted.restore(), true);
  assert.equal(restarted.snapshots[0].releaseId, 'release-a');
  await store.apply({ nodeId: 'node-a', snapshots: [] });
  assert.equal(store.snapshots.length, 0);
  assert.equal((new SnapshotStore(file, 'node-a')).snapshots.length, 0);
  await writeFile(file, 'broken');
  await assert.rejects(restarted.restore());
  assert.equal(restarted.snapshots.length, 1);
});

test('disk write failure does not activate a new snapshot', async (t) => {
  const dir = await mkdtemp(join(tmpdir(), 'sp-router-failure-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const parentFile = join(dir, 'not-a-directory');
  await writeFile(parentFile, 'occupied');
  const store = new SnapshotStore(join(parentFile, 'snapshot.json'), 'node-a');
  await assert.rejects(store.apply({ nodeId: 'node-a', snapshots: [envelope()] }));
  assert.equal(store.snapshots.length, 0);
});

test('directory durability barrier failure prevents activation and direct self-proxy domains are refused', async (t) => {
  const dir = await mkdtemp(join(tmpdir(), 'sp-router-durability-'));
  t.after(() => rm(dir, { recursive:true, force:true }));
  const store = new SnapshotStore(join(dir,'snapshot.json'),'node-a',{ durabilityBarrier:async () => {throw new Error('directory fsync failed');} });
  await assert.rejects(store.apply({nodeId:'node-a',snapshots:[envelope()]}));
  assert.equal(store.snapshots.length,0);
  const restartAfterFirstFailure = new SnapshotStore(join(dir,'snapshot.json'),'node-a');
  assert.equal(await restartAfterFirstFailure.restore(),false);
  const normal = new SnapshotStore(join(dir,'loop.json'),'node-a');
  await assert.rejects(normal.apply({nodeId:'node-a',snapshots:[envelope({defaultTarget:{id:'main',url:'https://www.example.com'}})]}));
});

test('rejected replacement cannot become the startup recovery version after a durability failure', async (t) => {
  const dir = await mkdtemp(join(tmpdir(),'sp-router-recovery-'));
  t.after(() => rm(dir,{recursive:true,force:true}));
  const file = join(dir,'snapshot.json');
  const good = new SnapshotStore(file,'node-a');
  await good.apply({nodeId:'node-a',snapshots:[envelope()]});
  const failing = new SnapshotStore(file,'node-a',{durabilityBarrier:async () => {throw new Error('fsync failure');}});
  await failing.restore();
  await assert.rejects(failing.apply({nodeId:'node-a',snapshots:[envelope({releaseId:'release-b'})]}));
  assert.equal(failing.snapshots[0].releaseId,'release-a');
  const restart = new SnapshotStore(file,'node-a');
  await restart.restore();
  assert.equal(restart.snapshots[0].releaseId,'release-a');
});
