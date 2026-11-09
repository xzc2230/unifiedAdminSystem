import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

// Validates the handoff manifests. This does not substitute for HTTP or business tests.
const directory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(directory, '../../..');
const errors = [];
const read = (name) => {
  const file = path.join(directory, name);
  if (!fs.existsSync(file)) {
    errors.push(`Missing manifest: ${name}`);
    return null;
  }
  return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
};
const check = (condition, message) => { if (!condition) errors.push(message); };
const checkFiles = (files, label) => {
  check(Array.isArray(files) && files.length > 0, `${label}: no concrete target file`);
  for (const file of files || []) {
    const resolved = path.resolve(root, file);
    const relative = path.relative(root, resolved);
    check(relative && !relative.startsWith('..') && !path.isAbsolute(relative), `${label}: file outside checkout: ${file}`);
    check(fs.existsSync(resolved) && fs.statSync(resolved).isFile(), `${label}: missing file: ${file}`);
  }
};
const key = (method, url) => `${method} ${url}`;
const audit = read('full-coverage-audit.json');
const foundation = read('foundation-targets.json');
const nonmigrated = read('nonmigrated-targets.json');
const components = read('component-targets.json');
const targets = read('target-endpoints.json');
const front = read('frontend-targets.json');
const snapshot = read('source-snapshot.json');

if (audit) {
  check(audit.endpoints.length === 235, 'Expected 235 source paths');
  check(audit.pages.length + audit.apis.length + audit.frontendSupport.length === 261, 'Expected 261 source frontend files');
  if (nonmigrated) {
    const expected = audit.endpoints.filter(e => ['工具差异登记', '示例不迁移'].includes(e.disposition));
    const mapped = new Map(nonmigrated.endpoints.map(e => [key(e.sourceMethod, e.sourcePath), e]));
    check(nonmigrated.endpoints.length === 17 && mapped.size === 17, 'Expected 17 explicit tool/example dispositions');
    for (const e of expected) {
      const target = mapped.get(key(e.httpMethod, e.path));
      check(Boolean(target), `Missing tool/example disposition: ${key(e.httpMethod, e.path)}`);
      if (target) {
        check(target.disposition === e.disposition && Boolean(target.reason), `Missing disposition reason: ${e.path}`);
        checkFiles(target.targetFiles, e.path);
      }
    }
  }
  if (foundation) {
    const expected = audit.endpoints.filter(e => e.disposition === '复用：宿主基础能力');
    const mapped = new Map(foundation.endpoints.map(e => [key(e.sourceMethod, e.sourcePath), e]));
    check(foundation.endpoints.length === 119 && mapped.size === 119, 'Expected 119 distinct foundation paths');
    for (const e of expected) check(mapped.has(key(e.httpMethod, e.path)), `Unmapped foundation path: ${key(e.httpMethod, e.path)}`);
    for (const e of foundation.endpoints) checkFiles(e.targetFiles, e.sourcePath);
  }
  if (targets) {
    const rows = Array.isArray(targets) ? targets : targets.endpoints;
    const dispositions = new Set(['迁移：管理业务', '迁移：旧客户端兼容', '补充迁移：资源兼容']);
    const expected = audit.endpoints.filter(e => dispositions.has(e.disposition));
    const mapped = new Map(rows.map(e => [key(e.sourceMethod, e.sourcePath), e]));
    check(rows.length === 99 && mapped.size === 99, 'Expected 99 distinct migrated source paths');
    for (const e of expected) {
      const target = mapped.get(key(e.httpMethod, e.path));
      check(Boolean(target), `Unmapped migrated path: ${key(e.httpMethod, e.path)}`);
      if (!target) continue;
      const prefix = e.disposition === '迁移：管理业务' ? '/package-admin' : '/package-legacy';
      check(target.targetPath.startsWith(`${prefix}/`), `Wrong namespace: ${target.targetPath}`);
      if (prefix === '/package-legacy') check(target.targetMethod === e.httpMethod, `Legacy method changed: ${key(e.httpMethod, e.path)}`);
      checkFiles([target.targetFile], e.path);
      check(Array.isArray(target.tests) && target.tests.length > 0, `No test evidence: ${e.path}`);
    }
  }
  if (front) {
    const rows = Array.isArray(front) ? front : front.files;
    const mapped = new Map(rows.map(e => [e.source, e]));
    check(rows.length === 261 && mapped.size === 261, 'Expected 261 distinct frontend source files');
    for (const e of [...audit.pages, ...audit.apis, ...audit.frontendSupport]) check(mapped.has(e.source), `Unmapped frontend file: ${e.source}`);
    for (const e of rows) checkFiles(e.targetFiles, e.source);
  }
}
if (components) {
  check(components.logic.length === 24, 'Expected 24 business/helper files');
  check(components.logic.reduce((sum, e) => sum + e.methods.length, 0) === 132, 'Expected 132 source method declarations');
  check(components.tables.length === 12 && new Set(components.tables.map(e => e.targetTable)).size === 12, 'Expected 12 distinct business tables');
  for (const e of [...components.logic, ...components.tables]) checkFiles(e.targetFiles, e.source || e.sourceTable);
  checkFiles(components.elasticsearch.targetFiles, 'Elasticsearch');
}
const sourceOption = process.argv.indexOf('--source-root');
if (sourceOption !== -1 && snapshot) {
  const sourceRoot = process.argv[sourceOption + 1];
  if (!sourceRoot) errors.push('--source-root requires a path');
  else for (const item of snapshot.files) {
    const file = path.join(sourceRoot, item.file);
    if (!fs.existsSync(file)) { errors.push(`Source missing: ${item.file}`); continue; }
    const digest = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    check(digest === item.sha256, `Source changed since audit: ${item.file}`);
  }
}
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else console.log(JSON.stringify({ sourcePaths: 235, migratedPaths: 99, foundationPaths: 119,
  frontendFiles: 261, businessHelperFiles: 24, tables: 12,
  sourceFingerprintsChecked: sourceOption === -1 ? 0 : snapshot.files.length,
  note: 'Manifest coverage and file existence verified; runtime evidence is recorded separately in verification.md' }, null, 2));
