import test from 'node:test';
import assert from 'node:assert/strict';
import { AddressPolicy, publicIpv4, cleanHeaders } from '../src/proxy.mjs';

test('deny reserved, private, loopback, link-local, multicast and IPv4 metadata destinations', () => {
  for (const ip of ['0.1.2.3','10.0.0.1','100.64.0.1','127.0.0.1','169.254.169.254','172.16.0.1','192.168.0.1','192.0.0.1','192.0.2.1','198.18.0.1','198.51.100.1','203.0.113.1','224.0.0.1','255.255.255.255','::1','::ffff:127.0.0.1']) {
    assert.equal(publicIpv4(ip), false, ip);
  }
  assert.equal(publicIpv4('93.184.216.34'), true);
});

test('resolution is validated per connection and never falls back to unchecked private answers', async () => {
  let calls = 0;
  const policy = new AddressPolicy('', async () => [{ address: calls++ === 0 ? '93.184.216.34' : '127.0.0.1', family: 4 }]);
  assert.equal((await policy.resolve(new URL('https://origin.example.com'))).address, '93.184.216.34');
  await assert.rejects(policy.resolve(new URL('https://origin.example.com')));
});

test('only exact operator-approved host and port is allowed to connect to a private IP', async () => {
  const policy = new AddressPolicy('origin.example.com:8080=127.0.0.1', async () => [{ address:'127.0.0.2', family:4 }]);
  assert.equal((await policy.resolve(new URL('http://origin.example.com:8080'))).address, '127.0.0.1');
  await assert.rejects(policy.resolve(new URL('http://origin.example.com:8081')));
  assert.throws(() => new AddressPolicy('*:8080=127.0.0.1'));
});

test('strip hop headers, connection nominated fields, forwarding and internal identity inputs', () => {
  const result = cleanHeaders({ host:'evil', connection:'x-secret,keep-alive', 'x-secret':'value', 'keep-alive':'x',
    forwarded:'for=evil', 'x-forwarded-for':'evil', 'x-real-ip':'evil', 'x-tenant-id':'999', 'x-user-group':'admin',
    'x-site-internal-auth':'bad', cookie:'session=ok', authorization:'Bearer app-token' }, true);
  assert.deepEqual(result, { cookie:'session=ok', authorization:'Bearer app-token' });
});
