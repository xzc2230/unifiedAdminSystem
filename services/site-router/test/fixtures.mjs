import { createHash } from 'node:crypto';

export function envelope(overrides = {}) {
  const payload = JSON.stringify({
    schemaVersion: 1, tenantId: 100, siteId: 'site-a', environment: 'test', releaseId: 'release-a',
    domains: ['www.example.com'], defaultTarget: { id: 'main', url: 'https://origin.example.com' },
    rules: [{ id: 'api', hostname: null, path: '/api', matchType: 'PREFIX', priority: 10,
      target: { id: 'api-target', url: 'https://api.example.com/base' } }], ...overrides,
  });
  return { payload, hash: createHash('sha256').update(payload).digest('hex') };
}
