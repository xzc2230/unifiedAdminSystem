import type { App } from 'vue';

/**
 * 动态引入 super 下的组件
 */
export async function registerSuper(app: App) {
  const online = await import('./online/register');
  await online.register(app);
  if (import.meta.env.VITE_ENABLE_AI === 'true') {
    const chat = await import('./airag/aiapp/chat/route/register');
    await chat.register(app);
  }
}
