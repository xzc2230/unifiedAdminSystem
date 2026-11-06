import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createConnection } from 'node:net';
import { once } from 'node:events';
import { createProxy, AddressPolicy } from '../src/proxy.mjs';
import { validateEnvelope } from '../src/snapshot.mjs';
import { envelope } from './fixtures.mjs';

async function listen(t, server) {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  return server.address().port;
}

function request(port, { path = '/', method = 'GET', headers = {}, body = '' } = {}) {
  return new Promise((resolve, reject) => {
    const outbound = http.request({ hostname:'127.0.0.1',port,path,method,headers: { host:'www.example.com',...headers } }, (response) => {
      let data = '';
      response.setEncoding('utf8'); response.on('data', (part) => { data += part; });
      response.on('end', () => resolve({ status:response.statusCode,headers:response.headers,body:data }));
      response.on('error', reject);
    });
    outbound.on('error', reject); outbound.end(body);
  });
}

async function setup(t, handler, options = {}) {
  const originPort = await listen(t, http.createServer(handler));
  const source = envelope({ defaultTarget:{ id:'main',url:`http://origin.example.com:${originPort}/base` },rules:[] });
  const store = { snapshots:[validateEnvelope(source)] };
  const policy = new AddressPolicy(`origin.example.com:${originPort}=127.0.0.1`);
  const port = await listen(t, createProxy(store,{ policy,externalProto:'https',...options }));
  return { port,store,originPort };
}

test('proxy streams POST with query and target base path, preserves public browser host through clean headers', async (t) => {
  const { port } = await setup(t, (incoming, response) => {
    let body = '';
    incoming.on('data', (part) => { body += part; });
    incoming.on('end', () => response.end(JSON.stringify({ method:incoming.method,path:incoming.url,headers:incoming.headers,body })));
  });
  const result = await request(port,{ path:'/items?q=1',method:'POST',headers:{ 'x-forwarded-host':'evil.example',
    'x-forwarded-proto':'http','x-forwarded-for':'169.254.169.254','x-user-group':'admin',cookie:'session=ok' },body:'hello' });
  assert.equal(result.status,200);
  const seen = JSON.parse(result.body);
  assert.equal(seen.method,'POST'); assert.equal(seen.path,'/base/items?q=1'); assert.equal(seen.body,'hello');
  assert.equal(seen.headers['x-forwarded-host'],'www.example.com'); assert.equal(seen.headers['x-forwarded-proto'],'https');
  assert.equal(seen.headers['x-user-group'],undefined); assert.notEqual(seen.headers['x-forwarded-for'],'169.254.169.254');
  assert.equal(seen.headers.cookie,'session=ok');
});

test('unknown host, ambiguous path and upload limit fail without calling upstream', async (t) => {
  let count = 0;
  const { port } = await setup(t, (_incoming,response) => { count++;response.end('origin'); }, { maxBodyBytes:4 });
  assert.equal((await request(port,{headers:{host:'foreign.example.com'}})).status,421);
  assert.equal((await request(port,{path:'/api%2fadmin'})).status,400);
  assert.equal((await request(port,{method:'POST',body:'too-large',headers:{'content-length':'9'}})).status,413);
  assert.equal(count,0);
});

test('upstream cookies and redirects pass through without following or rewriting HTML', async (t) => {
  let count = 0;
  const { port } = await setup(t, (_incoming,response) => {
    count++; response.writeHead(302,{location:'https://external.example.org/login','set-cookie':['a=1; Secure; HttpOnly','b=2; Path=/']});response.end('redirect');
  });
  const result = await request(port);
  assert.equal(result.status,302); assert.equal(result.headers.location,'https://external.example.org/login');
  assert.deepEqual(result.headers['set-cookie'],['a=1; Secure; HttpOnly','b=2; Path=/']); assert.equal(count,1);
});

test('upstream response arrives in chunks and timeout returns 504', async (t) => {
  const { port } = await setup(t, (_incoming,response) => { response.write('first'); setTimeout(() => response.end('last'), 20); });
  assert.equal((await request(port)).body,'firstlast');
  const slow = await setup(t, () => {}, { timeoutMs:50 });
  assert.equal((await request(slow.port)).status,504);
});

test('WebSocket upgrade is explicitly rejected until separately implemented', async (t) => {
  const { port } = await setup(t, (_incoming,response) => response.end('unused'));
  const socket = createConnection(port,'127.0.0.1');
  t.after(() => socket.destroy());
  await once(socket,'connect');
  socket.write('GET /socket HTTP/1.1\r\nHost: www.example.com\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n\r\n');
  const [data] = await once(socket,'data');
  assert.match(data.toString(),/^HTTP\/1.1 501/);
});

test('reserved control endpoints never reach an upstream, including a management base path', async (t) => {
  let count = 0;
  const {port} = await setup(t,(_request,response) => {count++;response.end('should-not-forward');});
  for (const path of ['/site-platform/node/snapshots','/jeecg-boot/site-platform/node/ack','/%73ite-platform/node/snapshots']) {
    assert.equal((await request(port,{path})).status,403,path);
  }
  assert.equal(count,0);
});

test('early upstream response closes unfinished upload within its configured request deadline', async (t) => {
  const {port} = await setup(t,(incoming,response) => {
    incoming.once('data',() => response.end('early'));
  },{timeoutMs:100});
  const socket = createConnection(port,'127.0.0.1');
  t.after(() => socket.destroy());
  await once(socket,'connect');
  const closed = new Promise((resolve) => {socket.once('close',() => resolve(true));});
  socket.on('error',() => {});socket.on('data',() => {});
  socket.write('POST /upload HTTP/1.1\r\nHost: www.example.com\r\nTransfer-Encoding: chunked\r\n\r\n1\r\nx\r\n');
  await new Promise((resolve) => setTimeout(resolve,30));
  if (!socket.destroyed) socket.write('10000\r\n'+'a'.repeat(65536)+'\r\n');
  const withinDeadline = await Promise.race([closed,new Promise((resolve) => setTimeout(() => resolve(false),250))]);
  assert.equal(withinDeadline,true);
});
