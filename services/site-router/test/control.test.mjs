import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { SnapshotStore } from '../src/snapshot.mjs';
import { ControlClient, synchronize } from '../src/control.mjs';
import { createProxy, AddressPolicy } from '../src/proxy.mjs';
import { envelope } from './fixtures.mjs';

test('apply then ACK exact bytes, controller downtime retains good local snapshot across restart, empty set removes it', async (t) => {
  const dir = await mkdtemp(join(tmpdir(),'sp-control-test-'));
  t.after(() => rm(dir,{ recursive:true,force:true }));
  let ack;
  let mode = 'good';
  const batch = { nodeId:'node-a',snapshots:[envelope()] };
  const controller = http.createServer((request,response) => {
    assert.equal(request.headers.authorization,'Bearer ' + 'a'.repeat(48));
    response.setHeader('content-type','application/json');
    if (mode === 'down') { response.writeHead(503);response.end('{}');return; }
    if (request.method === 'GET') { response.end(JSON.stringify(mode === 'empty' ? {nodeId:'node-a',snapshots:[]} : batch));return; }
    let raw = '';request.on('data',(part) => {raw += part;}); request.on('end',() => {ack = JSON.parse(raw);response.end('{"accepted":true}');});
  });
  controller.listen(0,'127.0.0.1'); await once(controller,'listening');
  t.after(() => {controller.closeAllConnections();controller.close();});
  const client = new ControlClient(`http://127.0.0.1:${controller.address().port}/jeecg-boot`,'a'.repeat(48));
  const file = join(dir,'snapshot.json');
  const store = new SnapshotStore(file,'node-a');
  await synchronize(store,client);
  assert.deepEqual(ack,{siteId:'site-a',releaseId:'release-a',hash:batch.snapshots[0].hash});
  mode = 'down';
  await assert.rejects(synchronize(store,client));
  assert.equal(store.snapshots[0].releaseId,'release-a');
  const restart = new SnapshotStore(file,'node-a');await restart.restore();
  assert.equal(restart.snapshots[0].releaseId,'release-a');
  mode = 'empty';await synchronize(restart,client);assert.equal(restart.snapshots.length,0);
});

test('reject unsafe controller URLs and short node credentials', () => {
  for (const url of ['http://control.example.com','https://u:p@control.example.com','https://control.example.com?secret=x']) {
    assert.throws(() => new ControlClient(url,'a'.repeat(48)));
  }
  assert.throws(() => new ControlClient('http://127.0.0.1:18080','short'));
});

test('a failed snapshot write never produces an acknowledgement', async () => {
  let acked = false;
  const client = { snapshots:async () => ({nodeId:'node-a',snapshots:[envelope()]}),ack:async () => {acked = true;} };
  const store = { apply:async () => {throw new Error('disk full');},snapshots:[] };
  await assert.rejects(synchronize(store,client));assert.equal(acked,false);
});
