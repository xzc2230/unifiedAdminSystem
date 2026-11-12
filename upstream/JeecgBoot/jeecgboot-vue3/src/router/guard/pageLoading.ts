import type { Router, RouteLocationNormalized } from 'vue-router';
import { isNavigationFailure, NavigationFailureType } from 'vue-router';

export function setupPageLoadingGuard(
  router: Router,
  options: { shouldLoad: (to: RouteLocationNormalized) => boolean; setLoading: (loading: boolean) => void }
) {
  let pending: RouteLocationNormalized | undefined;
  router.beforeEach((to) => {
    pending = to;
    // Also cancels an earlier delayed start before deciding to start again.
    options.setLoading(false);
    if (options.shouldLoad(to)) options.setLoading(true);
    return true;
  });
  const finish = (to: RouteLocationNormalized) => {
    if (pending !== to) return;
    pending = undefined;
    options.setLoading(false);
  };
  router.afterEach((to, _from, failure) => {
    // Duplicate navigation cancels a pending navigation without running beforeEach.
    if (isNavigationFailure(failure, NavigationFailureType.duplicated)) {
      pending = undefined;
      options.setLoading(false);
      return;
    }
    finish(to);
  });
  router.onError((_error, to) => finish(to));
}
