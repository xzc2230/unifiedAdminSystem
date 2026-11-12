interface AiMenu {
  path?: string;
  name?: string;
  originComponent?: unknown;
  component?: unknown;
  children?: AiMenu[];
}

/** True when the signed-in role was given the AI assistant menu. */
export function hasAiAssistantMenu(menus: AiMenu[] | undefined): boolean {
  if (!menus?.length) return false;
  return menus.some((menu) => isAiAssistantMenu(menu) || hasAiAssistantMenu(menu.children));
}

function isAiAssistantMenu(menu: AiMenu): boolean {
  const path = String(menu.path || '');
  const name = String(menu.name || '');
  const component = [menu.originComponent, menu.component].filter((value) => typeof value === 'string').join(' ');
  if (path === '/ai' || path.startsWith('/ai/')) return true;
  if (/aiapp\/chat|dashboard\/ai/.test(path) || /aiapp\/chat|dashboard\/ai/.test(component)) return true;
  return name === 'AI助手' || name === 'AI聊天';
}
