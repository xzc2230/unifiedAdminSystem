export class ControlClient {
  constructor(base, token, timeoutMs = 10000) {
    const url = new URL(base);
    if ((url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1','[::1]'].includes(url.hostname))) ||
      url.username || url.password || url.search || url.hash || typeof token !== 'string' || token.length < 32 || /[\s\x00-\x1f\x7f]/.test(token)) {
      throw new Error('Unsafe control URL or node credential');
    }
    this.base = url.href.replace(/\/$/,'');this.token = token;this.timeoutMs = timeoutMs;
  }
  async call(path, body) {
    const response = await fetch(this.base + path, {
      method: body ? 'POST' : 'GET',
      headers: { authorization:`Bearer ${this.token}`,accept:'application/json',...(body ? {'content-type':'application/json'} : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(this.timeoutMs),redirect:'error',
    });
    if (!response.ok) { await response.body?.cancel();throw new Error(`Control response ${response.status}`); }
    if (Number(response.headers.get('content-length') || 0) > 16 * 1024 * 1024) { await response.body?.cancel();throw new Error('Control response too large'); }
    const reader = response.body.getReader();
    const chunks = [];let size = 0;
    try {
      while (true) {
        const { value,done } = await reader.read();if (done) break;
        size += value.length;if (size > 16 * 1024 * 1024) throw new Error('Control response too large');
        chunks.push(value);
      }
    } finally { await reader.cancel().catch(() => {});reader.releaseLock(); }
    return JSON.parse(Buffer.concat(chunks,size).toString('utf8'));
  }
  snapshots() { return this.call('/site-platform/node/snapshots'); }
  ack(snapshot) { return this.call('/site-platform/node/ack',{ siteId:snapshot.siteId,releaseId:snapshot.releaseId,hash:snapshot.hash }); }
}

export async function synchronize(store,client) {
  const batch = await client.snapshots();
  await store.apply(batch);
  for (const snapshot of store.snapshots) {
    const result = await client.ack(snapshot);
    if (result?.accepted !== true) throw new Error('Snapshot acknowledgement rejected');
  }
  return store.snapshots.length;
}
