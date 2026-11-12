import type { AppRouteRecordRaw } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

const AI_ROUTE: AppRouteRecordRaw | undefined = import.meta.env.VITE_ENABLE_AI === 'true' ? {
  path: '',
  name: 'ai-parent',
  component: LAYOUT,
  meta: {
    title: 'ai',
  },
  children: [
    {
      path: '/ai',
      name: 'ai',
      component: () => import('/@/views/dashboard/ai/index.vue'),
      meta: {
        title: 'AI助手',
      },
    },
  ],
} : undefined;

export const staticRoutesList = AI_ROUTE ? [AI_ROUTE] : [];
