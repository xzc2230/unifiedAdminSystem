interface FeatureMenu {
  path?: string;
  component?: unknown;
  children?: FeatureMenu[];
  [key: string]: unknown;
}

export function filterAiMenus<T extends FeatureMenu>(menus: T[], enabled: boolean): T[] {
  if (enabled) return menus;
  return menus.flatMap((menu) => {
    const component = typeof menu.component === 'string' ? menu.component.replace(/^\//, '') : '';
    if (/^\/ai(?:\/|$)/.test(menu.path || '') || /^(?:super\/airag|dashboard\/ai)(?:\/|$)/.test(component)) return [];
    const children = menu.children ? filterAiMenus(menu.children, false) : undefined;
    if (menu.children?.length && !children?.length) return [];
    return [{ ...menu, ...(children ? { children } : {}) } as T];
  });
}
