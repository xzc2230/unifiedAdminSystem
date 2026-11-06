import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  canonicalRoutingPath,
  RoutingValidationError,
  routingValidationError,
  validateRoutingDraft,
  validateRoutingPath,
} from './routing.validation.ts';

const targets = [{ id: 't', enabled: true }];
const domains = [{ id: 'd', verified: true }];
const rule = (index = 0, overrides = {}) => ({
  id: `r${index}`,
  domainId: null,
  path: '/api',
  matchType: 'PREFIX',
  priority: index,
  targetId: 't',
  ...overrides,
});
const draft = (overrides = {}) => ({ revision: 7, defaultTargetId: 't', domainIds: ['d'], rules: [rule()], ...overrides });

test('accepts 100 unique rules and both inclusive priority boundaries', () => {
  const rules = Array.from({ length: 100 }, (_, index) => rule(index));
  rules[99].priority = 1_000_000;
  assert.equal(validateRoutingDraft(draft({ rules }), targets, domains), '');
});

test('rejects a 101st rule before sending the draft', () => {
  assert.match(validateRoutingDraft(draft({ rules: Array.from({ length: 101 }, (_, index) => rule(index)) }), targets, domains), /100/);
});

test('accepts 100 verified domains but rejects 101', () => {
  const rows = Array.from({ length: 101 }, (_, index) => ({ id: `d${index}`, verified: true }));
  const data = draft({ domainIds: rows.slice(0, 100).map(({ id }) => id) });
  assert.equal(validateRoutingDraft(data, targets, rows), '');
  assert.match(validateRoutingDraft({ ...data, domainIds: rows.map(({ id }) => id) }, targets, rows), /100/);
});

test('rejects negative, excessive, fractional and missing priorities', () => {
  for (const priority of [-1, 1_000_001, 0.5, null, NaN, Infinity]) {
    assert.match(validateRoutingDraft(draft({ rules: [rule(0, { priority })] }), targets, domains), /0.*1[,_]?000[,_]?000/);
  }
});

test('retains unique rule ID, priority and selected domain checks', () => {
  assert.match(validateRoutingDraft(draft({ rules: [rule(), rule(1, { priority: 0 })] }), targets, domains), /唯一/);
  assert.match(validateRoutingDraft(draft({ rules: [rule(), rule(1, { id: 'r0' })] }), targets, domains), /ID/);
  assert.match(validateRoutingDraft(draft({ rules: [rule(0, { domainId: 'other' })] }), targets, domains), /域名/);
});

test('retains resource availability checks for default and rule targets and domains', () => {
  assert.match(validateRoutingDraft(draft(), [{ id: 't', enabled: false }], domains), /默认目标/);
  assert.match(validateRoutingDraft(draft({ rules: [rule(0, { targetId: 'gone' })] }), targets, domains), /目标/);
  assert.match(validateRoutingDraft(draft(), targets, [{ id: 'd', verified: false }]), /域名/);
  assert.match(validateRoutingDraft(draft({ domainIds: [] }), targets, domains), /域名/);
  assert.equal(validateRoutingDraft(draft(), [{ id: 't', enabled: 1 }], [{ id: 'd', verified: 1 }]), '');
});

test('validates rule paths using the same path validator as simulation', () => {
  assert.match(validateRoutingDraft(draft({ rules: [rule(0, { path: '/a//b' })] }), targets, domains), /规则 1.*路径/);
});

test('accepts raw canonical paths, trailing slashes and legal percent escapes', () => {
  for (const path of ['/', '/api/', '/v1.2/file.json', '/.../a', '/a/%ab', '/%E4%B8%AD', "/a-_:~!$&'()*+,;=@/%25"]) {
    assert.equal(validateRoutingPath(path), '', path);
  }
});

test('accepts exactly 2048 characters and rejects 2049', () => {
  assert.equal(validateRoutingPath('/' + 'a'.repeat(2047)), '');
  assert.match(validateRoutingPath('/' + 'a'.repeat(2048)), /2048/);
});

test('rejects double slashes anywhere, dot segments and encoded dot or separators', () => {
  for (const path of [
    '//a',
    '/a//b',
    '/a//',
    '/.',
    '/..',
    '/./a',
    '/a/../b',
    '/a/.',
    '/a/..',
    '/%2e',
    '/a/%2Ehtml',
    '/a%2fb',
    '/a/%2F/b',
    '/a/%5c',
  ]) {
    assert.notEqual(validateRoutingPath(path), '', path);
  }
});

test('rejects malformed percent escapes, query, fragment, backslash and control characters', () => {
  for (const path of [
    '/a/%',
    '/a/%0',
    '/a/%xz',
    '/a?x=1',
    '/a#x',
    '/a\\b',
    '/a\0b',
    '/a\nb',
    '/a\rb',
    '/a\tb',
    '/a b',
    '/a\u007fb',
    '/a/%00',
    '/a/%0a',
    '/a/%0D',
  ]) {
    assert.notEqual(validateRoutingPath(path), '', JSON.stringify(path));
  }
});

test('rejects non-path URLs, raw non-ASCII and URI-illegal characters with useful feedback', () => {
  for (const path of ['', 'api', 'https://example.com/api', '/中文', '/a\u00a0b', '/a[b]', '/a{b}', '/a|b', '/a"b']) {
    assert.match(validateRoutingPath(path), /路径/, path);
  }
});

test('canonicalizes percent hex only, without decoding or changing matching semantics', () => {
  assert.equal(canonicalRoutingPath('/a/%ab/%e4%b8%ad/%25'), '/a/%AB/%E4%B8%AD/%25');
  assert.equal(canonicalRoutingPath('/api/'), '/api/');
  assert.throws(() => canonicalRoutingPath('/a/%2f'), RoutingValidationError);
});

test('classifies explicit HTTP 400 as editable validation failure and retains backend feedback', () => {
  const failure = { isAxiosError: true, response: { status: 400, data: { code: 400, message: '路径格式无效' } } };
  const validation = routingValidationError(failure);
  assert.ok(validation instanceof RoutingValidationError);
  assert.equal(validation.message, '路径格式无效');
  assert.ok(routingValidationError({ isAxiosError: true, response: { status: 400 } }) instanceof RoutingValidationError);
  const local = new RoutingValidationError('请选择正确路径');
  assert.equal(routingValidationError(local), local);
});

test('keeps conflicts, ambiguous network outcomes and unclassified failures refresh-required', () => {
  for (const cause of [
    { isAxiosError: true, response: { status: 409, data: { code: 400 } } },
    { isAxiosError: true, response: { status: 400, data: { code: 409 } } },
    { isAxiosError: true, response: { status: 500, data: { code: 400 } } },
    { isAxiosError: true, code: 'ECONNABORTED' },
    new Error('Network Error'),
    new Error('400: invalid draft'),
    { response: { status: 400 } },
    null,
  ]) {
    assert.equal(routingValidationError(cause), undefined);
  }
});
