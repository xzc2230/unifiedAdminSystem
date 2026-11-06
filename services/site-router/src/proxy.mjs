import http from 'node:http';
import https from 'node:https';
import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import { Transform } from 'node:stream';
import { hostname, matchRequest } from './snapshot.mjs';

export function publicIpv4(address) {
  if (isIP(address) !== 4) return false; // IPv6 intentionally fails closed until a full public-prefix policy is validated.
  const [a,b,c] = address.split('.').map(Number);
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) || (a === 192 && (b === 168 || b === 0 || b === 88 && c === 99)) ||
    (a === 198 && (b === 18 || b === 19 || b === 51 && c === 100)) || (a === 203 && b === 0 && c === 113));
}

export class AddressPolicy {
  constructor(allowlist = '', resolver = lookup) {
    this.resolver = resolver;
    this.allowed = new Map();
    for (const entry of allowlist.split(',').filter(Boolean)) {
      const match = /^([^:=]+):([1-9][0-9]{0,4})=(.+)$/.exec(entry);
      if (!match || hostname(match[1]) !== match[1] || Number(match[2]) > 65535 || !isIP(match[3])) throw new Error('Invalid operator upstream allowlist');
      const key = `${match[1]}:${match[2]}`;
      if (this.allowed.has(key)) throw new Error('Duplicate operator upstream');
      this.allowed.set(key, match[3]);
    }
  }
  async resolve(target) {
    const port = Number(target.port || (target.protocol === 'https:' ? 443 : 80));
    const approved = this.allowed.get(`${target.hostname}:${port}`);
    if (approved) return { address: approved, family: isIP(approved) };
    const addresses = await this.resolver(target.hostname, { all: true, family: 4, verbatim: true });
    if (!addresses.length || addresses.some((entry) => !publicIpv4(entry.address))) throw new Error('Upstream address denied');
    return addresses[0];
  }
}

const HOP = new Set(['connection','keep-alive','proxy-authenticate','proxy-authorization','proxy-connection','te','trailer','transfer-encoding','upgrade']);
export function cleanHeaders(headers, request = false) {
  const denied = new Set([...HOP, ...String(headers.connection || '').toLowerCase().split(',').map((item) => item.trim())]);
  const result = {};
  for (const [key,value] of Object.entries(headers)) {
    const lower = key.toLowerCase();
    if (denied.has(lower) || value === undefined) continue;
    if (request && (lower === 'host' || lower === 'forwarded' || lower.startsWith('x-forwarded-') || lower === 'x-real-ip' ||
      lower === 'x-tenant-id' || lower === 'x-user-id' || lower === 'x-user-group' || lower.startsWith('x-site-') || lower === 'x-internal-auth')) continue;
    result[lower] = value;
  }
  return result;
}

function reply(response, code, message) {
  if (response.destroyed || response.writableEnded) return;
  if (response.headersSent) { response.destroy(); return; }
  response.writeHead(code, { 'content-type':'text/plain; charset=utf-8', 'cache-control':'no-store' });
  response.end(message);
}

export function createProxy(store, { policy = new AddressPolicy(), timeoutMs = 30000, maxBodyBytes = 64 * 1024 * 1024, externalProto = 'https' } = {}) {
  if (!['http','https'].includes(externalProto)) throw new Error('Invalid external protocol');
  const server = http.createServer(async (request, response) => {
    let route;
    try { route = matchRequest(store.snapshots, request.headers.host, request.url); }
    catch { reply(response, 400, 'Invalid host or path'); return; }
    if (!route) { reply(response, 421, 'Unknown site'); return; }
    if (!['GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS'].includes(request.method)) { reply(response, 405, 'Method not allowed'); return; }
    if (Number(request.headers['content-length'] || 0) > maxBodyBytes) { reply(response, 413, 'Request too large'); return; }
    let upstream;
    let limiter;
    let sourceResponse;
    let ended = false;
    const cleanup = () => {
      ended = true; clearTimeout(timer);
      upstream?.destroy(); sourceResponse?.destroy();
      if (limiter) { request.unpipe(limiter); limiter.unpipe(); limiter.destroy(); }
      if (!request.complete) request.socket.destroySoon();
    };
    const timer = setTimeout(() => {
      ended = true; upstream?.destroy(); reply(response, 504, 'Upstream timeout');
    }, timeoutMs);
    timer.unref();
    response.once('close', cleanup);
    response.once('finish', cleanup);
    request.once('aborted', () => { ended = true; upstream?.destroy(); clearTimeout(timer); });
    try {
      const target = new URL(route.target.url);
      const fullPath = target.pathname.replace(/\/$/, '') + request.url.split('?',1)[0];
      const controlPath = fullPath.replace(/%([a-f0-9]{2})/gi, (_part,hex) => String.fromCharCode(parseInt(hex,16))).replace(/;[^/]*/g,'');
      if (/(?:^|\/)site-platform\/node(?:\/|$)/.test(controlPath)) { reply(response,403,'Reserved control path');return; }
      const address = await policy.resolve(target);
      if (ended) return;
      const headers = cleanHeaders(request.headers, true);
      headers.host = target.host;
      headers['x-forwarded-host'] = hostname(request.headers.host, true);
      headers['x-forwarded-proto'] = externalProto;
      // No inbound forwarding/IP/identity header is trusted in this phase.
      headers['x-forwarded-for'] = request.socket.remoteAddress;
      const prefix = target.pathname.replace(/\/$/, '');
      upstream = (target.protocol === 'https:' ? https : http).request({
        hostname: target.hostname, servername: target.hostname, port: target.port || undefined,
        method: request.method, path: prefix + request.url, headers,
        agent: false, // No cross-request connection pool can bypass per-connection DNS checks.
        lookup: (_name, options, callback) => options.all ? callback(null, [address]) : callback(null, address.address, address.family),
      }, (source) => {
        sourceResponse = source;
        if (ended) { source.destroy(); return; }
        if (source.statusCode === 101) { source.destroy(); reply(response, 502, 'Unexpected protocol upgrade'); return; }
        if (!request.complete) response.shouldKeepAlive = false;
        response.writeHead(source.statusCode, cleanHeaders(source.headers));
        source.on('error', () => reply(response, 502, 'Upstream stream failed'));
        source.on('aborted', () => reply(response, 502, 'Upstream stream aborted'));
        source.pipe(response);
      });
      upstream.on('upgrade', (_res, socket) => { socket.destroy(); reply(response, 502, 'Unsupported upstream upgrade'); });
      upstream.on('error', () => { if (!ended) reply(response, 502, 'Upstream unavailable'); });
      let bytes = 0;
      const limit = new Transform({ transform(chunk, _encoding, callback) {
        bytes += chunk.length;
        if (bytes > maxBodyBytes) {
          ended = true; upstream.destroy(); reply(response, 413, 'Request too large'); callback(new Error('Body limit exceeded'));
        } else callback(null, chunk);
      } });
      limiter = limit;
      limit.on('error', () => { request.unpipe(limit); request.resume(); });
      request.on('error', () => upstream.destroy());
      request.pipe(limit).pipe(upstream);
    } catch { if (!ended) reply(response, 502, 'Upstream address unavailable or denied'); }
  });
  server.on('upgrade', (_request, socket) => { socket.end('HTTP/1.1 501 Not Implemented\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'); });
  server.on('connect', (_request, socket) => socket.end('HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\nContent-Length: 0\r\n\r\n'));
  server.headersTimeout = 15000;
  server.requestTimeout = Math.max(timeoutMs, 15000);
  server.keepAliveTimeout = 5000;
  server.maxHeadersCount = 100;
  return server;
}
