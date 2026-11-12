import { onActivated } from 'vue';
import { createPageRefreshPolicy } from './pageRefreshPolicy';

export function useCachedPageRefresh(refresh: () => Promise<unknown>, maxAge = 30_000) {
  const policy = createPageRefreshPolicy(maxAge);
  onActivated(async () => {
    try {
      await policy.activate(refresh);
    } catch {
      // HTTP/table layers display errors; a failure stays stale and can retry.
    }
  });
  return policy;
}
