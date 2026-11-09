import test from 'node:test';
import assert from 'node:assert/strict';

const helpers = await import('../src/views/attribution/attribution.form.mjs').catch(() => ({}));
const form = () => ({ applicationName: ' 应用 A ', channelName: ' 渠道 A ', fallbackUrl: 'https://example.com/fallback', offers: [] });
const offer = (key = 'one') => ({ clientKey: key, targetUrl: 'https://example.com/offer', weight: 1, matchMode: 'AND', conditions: [] });

test('form helpers are available', () => assert.equal(typeof helpers.validateForm, 'function'));
test('first Offer has weight 1, subsequent Offers have weight 0 and independent conditions', () => {
  const first = helpers.newOffer(0, 'first');
  const second = helpers.newOffer(1, 'second');
  assert.equal(first.weight, 1);
  assert.equal(second.weight, 0);
  first.conditions.push({ field: 'OS', operator: 'IS', values: ['IOS'] });
  assert.deepEqual(second.conditions, []);
});
test('empty Offers are legal; names are trimmed in payload', () => {
  assert.deepEqual(helpers.validateForm(form()), []);
  assert.equal(helpers.buildPayload(form()).applicationName, '应用 A');
});
test('all zero weights are rejected but zero weight Offers remain in payload', () => {
  const value = form();
  value.offers = [{ ...offer(), weight: 0 }];
  assert.ok(helpers.validateForm(value).some((error) => error.path === 'offers'));
  value.offers.push(offer('two'));
  assert.deepEqual(helpers.validateForm(value), []);
  assert.equal(helpers.buildPayload(value).offers.length, 2);
  assert.equal(helpers.buildPayload(value).offers[0].weight, 0);
});
test('negative, fractional, missing and unsafe weights are rejected at the card', () => {
  for (const weight of [-1, 0.5, null, Number.MAX_SAFE_INTEGER + 1]) {
    const errors = helpers.validateForm({ ...form(), offers: [{ ...offer(), weight }] });
    assert.ok(errors.some((error) => error.path === 'offers[0].weight'));
  }
});
test('required names and HTTPS URLs are validated without treating fallback as an Offer', () => {
  const errors = helpers.validateForm({
    ...form(),
    applicationName: ' ',
    fallbackUrl: 'http://example.com',
    offers: [{ ...offer(), targetUrl: 'javascript:alert(1)' }],
  });
  assert.ok(errors.some((error) => error.path === 'applicationName'));
  assert.ok(errors.some((error) => error.path === 'fallbackUrl'));
  assert.ok(errors.some((error) => error.path === 'offers[0].targetUrl'));
});
test('conditions require supported fields, operators, OS values and nonempty sets', () => {
  for (const condition of [
    { field: 'DEVICE', operator: 'IS', values: ['IOS'] },
    { field: 'OS', operator: 'NOT', values: ['IOS'] },
    { field: 'OS', operator: 'IS', values: [] },
    { field: 'OS', operator: 'IS_NOT', values: ['UNKNOWN'] },
  ]) {
    const errors = helpers.validateForm({ ...form(), offers: [{ ...offer(), conditions: [condition] }] });
    assert.ok(errors.some((error) => error.path.startsWith('offers[0].conditions[0]')));
  }
});
test('COUNTRY is unavailable by default; unchanged existing COUNTRY rules survive', () => {
  const value = { ...form(), offers: [{ ...offer(), bindingId: 'binding-a', conditions: [{ field: 'COUNTRY', operator: 'IS', values: ['US'] }] }] };
  assert.ok(helpers.validateForm(value).length > 0);
  assert.deepEqual(helpers.validateForm(value, { originalOffers: structuredClone(value.offers) }), []);
  assert.deepEqual(helpers.validateForm(value, { countryEnabled: true }), []);
  assert.ok(
    helpers.validateForm(
      { ...value, offers: [{ ...value.offers[0], conditions: [{ field: 'COUNTRY', operator: 'IS', values: ['USA'] }] }] },
      { countryEnabled: true }
    ).length > 0
  );
});
test('edit hydration preserves string IDs, version, sort order and clones source conditions', () => {
  const source = {
    ...form(),
    expectedConfigVersion: '9007199254740993',
    managedBindingIds: ['b2', 'b1'],
    offers: [
      { ...offer('two'), bindingId: 'b2', sortOrder: 1 },
      { ...offer(), bindingId: 'b1', sortOrder: 0, conditions: [{ field: 'OS', operator: 'IS', values: ['IOS'] }] },
    ],
  };
  const hydrated = helpers.hydrateForm(source, () => 'generated');
  assert.equal(hydrated.expectedConfigVersion, source.expectedConfigVersion);
  assert.equal(hydrated.offers[0].bindingId, 'b1');
  hydrated.offers[0].conditions[0].values.push('WEB');
  assert.deepEqual(source.offers[1].conditions[0].values, ['IOS']);
});
test('edit payload explicitly removes only omitted managed bindings, including removing all', () => {
  const value = {
    ...form(),
    expectedConfigVersion: '9',
    managedBindingIds: ['b1', 'b2'],
    offers: [{ ...offer(), bindingId: 'b2', offerId: 'hidden' }],
  };
  const payload = helpers.buildPayload(value);
  assert.deepEqual(payload.removedBindingIds, ['b1']);
  assert.equal(payload.expectedConfigVersion, '9');
  assert.equal(payload.offers[0].bindingId, 'b2');
  assert.equal('offerId' in payload.offers[0], false);
  value.offers = [];
  assert.deepEqual(helpers.buildPayload(value).removedBindingIds, ['b1', 'b2']);
  assert.equal('expectedConfigVersion' in helpers.buildPayload(form()), false);
});
test('retry keeps its key for the same payload and renews after any payload change', () => {
  let sequence = 0;
  const retry = helpers.createSaveAttempt(() => `key-${++sequence}`);
  const value = form();
  const first = retry.prepare(helpers.buildPayload(value));
  assert.equal(retry.prepare(helpers.buildPayload(value)).key, first.key);
  value.channelName = 'changed';
  const changed = retry.prepare(helpers.buildPayload(value));
  assert.notEqual(changed.key, first.key);
  assert.equal(retry.prepare(helpers.buildPayload(value)).key, changed.key);
  value.channelName = ' 渠道 A ';
  assert.notEqual(retry.prepare(helpers.buildPayload(value)).key, first.key);
});
test('backend Result and Axios errors retain fieldErrors and flag conflicts', () => {
  const fieldErrors = [{ path: 'offers[1].weight', message: '权重错误' }];
  for (const raw of [
    { code: 409, message: '版本冲突', fieldErrors },
    { response: { status: 409, data: { message: '版本冲突', result: { fieldErrors } } } },
  ]) {
    const error = helpers.readError(raw);
    assert.equal(error.conflict, true);
    assert.deepEqual(error.fieldErrors, fieldErrors);
    assert.match(error.message, /版本冲突/);
  }
});
test('field errors can address a card by clientKey rather than its current index', () => {
  const errors = helpers.readError({ fieldErrors: [{ clientKey: 'two', path: 'targetUrl', message: '域名未获准' }] }, [offer(), offer('two')]);
  assert.equal(errors.fieldErrors[0].path, 'offers[1].targetUrl');
});
test('summaries retain per-Offer AND/OR and IS_NOT multivalue semantics', () => {
  assert.equal(helpers.conditionSummary(offer()), '不限');
  const summary = helpers.conditionSummary({
    ...offer(),
    matchMode: 'OR',
    conditions: [
      { field: 'OS', operator: 'IS_NOT', values: ['ANDROID', 'IOS'] },
      { field: 'COUNTRY', operator: 'IS', values: ['US'] },
    ],
  });
  assert.match(summary, /不是/);
  assert.match(summary, /或/);
  assert.match(summary, /Android/);
});
