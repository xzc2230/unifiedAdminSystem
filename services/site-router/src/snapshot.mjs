import { createHash, randomUUID } from 'node:crypto';
import { copyFile, mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { dirname } from 'node:path';
import { domainToASCII } from 'node:url';

const fail = (message) => { throw new Error(message); };
const identifier = (value) => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,32}$/.test(value);

export function hostname(value, allowPort = false) {
  if (typeof value !== 'string' || value.length > 260 || /[\s/@?#\\]/.test(value)) fail('Invalid host');
  let raw = value;
  if (allowPort) raw = raw.replace(/:[1-9][0-9]{0,4}$/, '');
  const name = domainToASCII(raw.replace(/\.$/, '')).toLowerCase();
  if (name.length > 253 || !name.includes('.') || /^[0-9.]+$/.test(name) ||
      /\.(local|localhost|internal)$/.test(name) ||
      !name.split('.').every((label) => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label))) fail('Invalid host');
  return name;
}

export function requestPath(raw) {
  if (typeof raw !== 'string' || raw.length > 8192 || /[\x00-\x20\x7f\\#]/.test(raw) || !raw.startsWith('/')) fail('Invalid request path');
  const path = raw.split('?', 1)[0];
  if (path.includes('//') || /%(?![0-9a-f]{2})/i.test(path) || /%(?:2e|2f|5c)/i.test(path) ||
      path.split('/').some((segment) => segment === '.' || segment === '..')) fail('Ambiguous request path');
  return path.replace(/%[0-9a-f]{2}/gi, (part) => part.toUpperCase());
}

function validateTarget(value) {
  if (!value || !identifier(value.id) || typeof value.url !== 'string' || value.url.length > 2000) fail('Invalid target');
  const target = new URL(value.url);
  if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password || target.search || target.hash ||
      hostname(target.hostname) !== target.hostname || (target.port && Number(target.port) < 1)) fail('Invalid target URL');
  requestPath(target.pathname);
  return Object.freeze({ id: value.id, url: value.url });
}

export function validateEnvelope(envelope) {
  if (!envelope || typeof envelope.payload !== 'string' || Buffer.byteLength(envelope.payload) > 2 * 1024 * 1024 ||
      !/^[a-f0-9]{64}$/.test(envelope.hash || '') || createHash('sha256').update(envelope.payload).digest('hex') !== envelope.hash) fail('Snapshot hash mismatch');
  const data = JSON.parse(envelope.payload);
  if (data.schemaVersion !== 1 || !Number.isSafeInteger(data.tenantId) || data.tenantId < 1 ||
      !identifier(data.siteId) || !identifier(data.releaseId) || !['dev','test','staging','prod'].includes(data.environment) ||
      !Array.isArray(data.domains) || !data.domains.length || data.domains.length > 100 ||
      !Array.isArray(data.rules) || data.rules.length > 100) fail('Invalid snapshot identity or limits');
  const domains = data.domains.map((name) => { if (hostname(name) !== name) fail('Noncanonical domain'); return name; });
  if (new Set(domains).size !== domains.length) fail('Duplicate snapshot domain');
  const defaultTarget = validateTarget(data.defaultTarget);
  const ids = new Set();
  const priorities = new Set();
  const targets = new Map([[defaultTarget.id, defaultTarget.url]]);
  const rules = data.rules.map((rule) => {
    if (!identifier(rule.id) || ids.has(rule.id) || !Number.isInteger(rule.priority) || rule.priority < 0 || rule.priority > 1000000 ||
        priorities.has(rule.priority) || !['EXACT','PREFIX'].includes(rule.matchType) ||
        (rule.hostname !== null && !domains.includes(rule.hostname)) || rule.path.includes('?') || requestPath(rule.path) !== rule.path) fail('Invalid rule');
    const target = validateTarget(rule.target);
    if (targets.has(target.id) && targets.get(target.id) !== target.url) fail('Inconsistent target identity');
    targets.set(target.id, target.url);
    ids.add(rule.id); priorities.add(rule.priority);
    return Object.freeze({ id: rule.id, hostname: rule.hostname, path: rule.path, matchType: rule.matchType, priority: rule.priority, target });
  }).sort((a,b) => b.priority - a.priority);
  return Object.freeze({ schemaVersion: 1, tenantId: data.tenantId, siteId: data.siteId, environment: data.environment,
    releaseId: data.releaseId, hash: envelope.hash, domains: Object.freeze(domains), defaultTarget, rules: Object.freeze(rules) });
}

export function matchRequest(snapshots, host, rawPath) {
  const name = hostname(host, true);
  const path = requestPath(rawPath);
  const snapshot = snapshots.find((entry) => entry.domains.includes(name));
  if (!snapshot) return null;
  const rule = snapshot.rules.find((item) => (item.hostname === null || item.hostname === name) &&
    (item.matchType === 'EXACT' ? path === item.path : item.path === '/' || path === item.path || path.startsWith(item.path.replace(/\/$/, '') + '/')));
  return { snapshot, ruleId: rule?.id ?? null, target: rule?.target ?? snapshot.defaultTarget };
}

export class SnapshotStore {
  #snapshots = Object.freeze([]);
  constructor(file, nodeId, { durabilityBarrier = syncDirectory } = {}) {
    if (!identifier(nodeId)) fail('Invalid node id');
    this.file = file; this.nodeId = nodeId; this.durabilityBarrier = durabilityBarrier;
  }
  get snapshots() { return this.#snapshots; }
  validate(batch) {
    if (!batch || batch.nodeId !== this.nodeId || !Array.isArray(batch.snapshots) || batch.snapshots.length > 1000) fail('Invalid node snapshot set');
    const snapshots = batch.snapshots.map(validateEnvelope);
    const sites = new Set(); const domains = new Set();
    for (const snapshot of snapshots) {
      // Site ids are globally unique in the resource schema.
      if (sites.has(snapshot.siteId)) fail('Duplicate site');
      sites.add(snapshot.siteId);
      for (const domain of snapshot.domains) { if (domains.has(domain)) fail('Domain ownership conflict'); domains.add(domain); }
    }
    for (const snapshot of snapshots) {
      for (const target of [snapshot.defaultTarget,...snapshot.rules.map((rule) => rule.target)]) {
        if (domains.has(new URL(target.url).hostname)) fail('Upstream points to a hosted domain');
      }
    }
    return Object.freeze(snapshots);
  }
  async restore() {
    let raw;
    try { raw = await readFile(this.file, 'utf8'); } catch (error) { if (error.code === 'ENOENT') return false; throw error; }
    if (Buffer.byteLength(raw) > 16 * 1024 * 1024) fail('Stored snapshot too large');
    const next = this.validate(JSON.parse(raw));
    this.#snapshots = next;
    return true;
  }
  async apply(batch) {
    const next = this.validate(batch);
    const serialized = JSON.stringify(batch);
    if (Buffer.byteLength(serialized) > 16 * 1024 * 1024) fail('Snapshot set too large');
    await mkdir(dirname(this.file), { recursive: true });
    const temporary = `${this.file}.${randomUUID()}.tmp`;
    const backup = `${temporary}.previous`;
    let previous = false;
    let replaced = false;
    let preserveBackup = false;
    try {
      try {
        await copyFile(this.file,backup);
        const handle = await open(backup,'r+');
        try { await handle.sync(); } finally { await handle.close(); }
        previous = true;
      } catch (error) { if (error.code !== 'ENOENT') throw error; }
      const handle = await open(temporary, 'wx', 0o600);
      try { await handle.writeFile(serialized, 'utf8'); await handle.sync(); } finally { await handle.close(); }
      await rename(temporary, this.file);
      replaced = true;
      await this.durabilityBarrier(dirname(this.file));
    } catch (error) {
      if (replaced) {
        // Undo the replacement too: an unacknowledged failed commit must not
        // silently become the next startup's recovery version.
        try {
          if (previous) await rename(backup,this.file);
          else await unlink(this.file);
          await syncDirectory(dirname(this.file));
        } catch (recoveryError) {
          preserveBackup = true;
          throw new AggregateError([error,recoveryError],`Snapshot persistence and recovery failed; inspect ${backup} before restarting`);
        }
      }
      throw error;
    } finally {
      await unlink(temporary).catch(() => {});
      if (!preserveBackup) await unlink(backup).catch(() => {});
    }
    this.#snapshots = next;
  }
}

async function syncDirectory(directory) {
  // POSIX production: sync the rename's directory entry before activation/ACK.
  // Node/Windows cannot portably fsync directory handles; local development
  // retains atomic replacement but does not claim power-loss durability.
  if (process.platform === 'win32') return;
  const handle = await open(directory, 'r');
  try { await handle.sync(); } finally { await handle.close(); }
}
