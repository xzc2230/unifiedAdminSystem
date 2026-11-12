/** Data freshness is independent of whether a cached component is mounted. */
export function createPageRefreshPolicy(maxAge: number, now: () => number = Date.now) {
  let firstActivation = true;
  let refreshedAt: number | undefined;
  let pending: Promise<void> | undefined;
  let requests = 0;
  let requestSequence = 0;
  return {
    markFresh() {
      refreshedAt = now();
    },
    async trackRequest<T>(request: () => Promise<T>): Promise<T> {
      const sequence = ++requestSequence;
      requests++;
      try {
        const value = await request();
        if (sequence === requestSequence) refreshedAt = now();
        return value;
      } catch (error) {
        if (sequence === requestSequence) refreshedAt = undefined;
        throw error;
      } finally {
        requests--;
      }
    },
    async activate(refresh: () => Promise<unknown>) {
      // BasicTable owns the initial request on mount.
      if (firstActivation) {
        firstActivation = false;
        return;
      }
      if (pending) return pending;
      if (requests) return;
      if (refreshedAt !== undefined && now() - refreshedAt < maxAge) return;
      const sequence = requestSequence;
      pending = (async () => {
        await refresh();
        // A tracked list request owns freshness when refresh triggered one.
        if (sequence === requestSequence) refreshedAt = now();
      })();
      try {
        await pending;
      } finally {
        pending = undefined;
      }
    },
  };
}
