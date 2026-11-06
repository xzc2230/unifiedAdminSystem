import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SnapshotStore } from '../src/snapshot.mjs';
import { envelope } from './fixtures.mjs';

test('independent runtime process serves persisted published traffic while controller is offline', async (t) => {
  const dir = await mkdtemp(join(tmpdir(),'sp-runtime-test-'));
  t.after(() => rm(dir,{recursive:true,force:true}));
  const origin = http.createServer((_request,response) => response.end('last-known-good'));
  origin.listen(0,'127.0.0.1');await once(origin,'listening');
  t.after(() => {origin.closeAllConnections();origin.close();});
  const originPort = origin.address().port;
  const reserve = http.createServer();reserve.listen(0,'127.0.0.1');await once(reserve,'listening');
  const port = reserve.address().port;
  await new Promise((resolve) => reserve.close(resolve));
  const file = join(dir,'snapshots.json');
  const store = new SnapshotStore(file,'node-a');
  await store.apply({nodeId:'node-a',snapshots:[envelope({defaultTarget:{id:'main',url:`http://origin.example.com:${originPort}`},rules:[]})]});
  const child = spawn(process.execPath,[fileURLToPath(new URL('../src/main.mjs',import.meta.url))],{
    env:{...process.env,SP_NODE_ID:'node-a',SP_NODE_TOKEN:'a'.repeat(48),SP_CONTROL_URL:'http://127.0.0.1:1',
      SP_PORT:String(port),SP_BIND:'127.0.0.1',SP_SNAPSHOT_FILE:file,SP_ALLOWED_UPSTREAMS:`origin.example.com:${originPort}=127.0.0.1`},
    stdio:['ignore','pipe','pipe'],windowsHide:true,
  });
  t.after(async () => {if (child.exitCode === null) {child.kill();await once(child,'exit');}});
  let stdout = '';let stderr = '';
  child.stderr.on('data',(part) => {stderr += part;});
  const ready = new Promise((resolve,reject) => {
    child.stdout.on('data',(part) => {stdout += part;if (stdout.includes('listener_started')) resolve();});
    child.once('error',reject);child.once('exit',() => reject(new Error('Runtime exited: '+stderr)));
  });
  await Promise.race([ready,new Promise((_resolve,reject) => {const timer = setTimeout(() => reject(new Error('Runtime timeout')),10000);timer.unref();})]);
  const body = await new Promise((resolve,reject) => {
    http.get({hostname:'127.0.0.1',port,headers:{host:'www.example.com'}},(response) => {
      assert.equal(response.statusCode,200);let result='';response.on('data',(part) => {result += part;});response.on('end',() => resolve(result));
    }).on('error',reject);
  });
  assert.equal(body,'last-known-good');assert.equal(stdout.includes('a'.repeat(48)),false);
});
