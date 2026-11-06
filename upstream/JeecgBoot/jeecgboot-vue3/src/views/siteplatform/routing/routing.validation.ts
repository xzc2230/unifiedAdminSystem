import type { DraftInput } from './routing.api';
import type { Domain, Target } from '../resources/resources.api';

export class RoutingValidationError extends Error {}

export const MAX_ROUTING_ITEMS = 100;
export const MAX_ROUTING_PRIORITY = 1_000_000;
export const MAX_ROUTING_PATH_LENGTH = 2048;

export function validateRoutingPath(path: string): string {
  if (typeof path !== 'string' || !path.startsWith('/')) return '路径须以 / 开头。';
  if (path.length > MAX_ROUTING_PATH_LENGTH) return `路径不能超过 ${MAX_ROUTING_PATH_LENGTH} 个字符。`;
  if (/[^A-Za-z0-9._~!$&'()*+,;=:@/%-]/.test(path))
    return '路径只能包含合法 ASCII URI 字符，不能包含查询参数、片段、反斜杠、控制字符或空白；中文等字符请使用 percent 编码。';
  if (path.includes('//')) return '路径任意位置均不能包含连续的 //。';
  if (/(?:^|\/)\.{1,2}(?:\/|$)/.test(path)) return '路径不能包含 . 或 .. 路径段。';
  if (/%(?![0-9A-Fa-f]{2})/.test(path)) return '路径中的 % 后必须紧跟两位十六进制字符，例如 %E4。';
  if (/%(?:2e|2f|5c|00|0a|0d)/i.test(path)) return '路径不能包含编码的点、斜杠、反斜杠或 NUL/换行（如 %2E、%2F、%5C）。';
  return '';
}

export function canonicalRoutingPath(path: string): string {
  const error = validateRoutingPath(path);
  if (error) throw new RoutingValidationError(error);
  return path.replace(/%[0-9A-Fa-f]{2}/g, (escape) => escape.toUpperCase());
}

export function routingValidationError(cause: unknown): RoutingValidationError | undefined {
  if (cause instanceof RoutingValidationError) return cause;
  if (!cause || typeof cause !== 'object') return undefined;
  // A plain Error has already lost its status. Never infer a safe retry from its message.
  const failure = cause as { isAxiosError?: unknown; response?: { status?: unknown; data?: { code?: unknown; message?: unknown } } };
  const response = failure.response;
  if (failure.isAxiosError !== true || response?.status !== 400 || (response.data?.code !== undefined && response.data.code !== 400))
    return undefined;
  const message = response.data?.message;
  return new RoutingValidationError(typeof message === 'string' && message.trim() ? message : '输入校验失败，请修改表单后重试。');
}

export function validateRoutingDraft(
  data: DraftInput,
  targets: readonly Pick<Target, 'id' | 'enabled'>[],
  domains: readonly Pick<Domain, 'id' | 'verified'>[]
): string {
  const flagOn = (value: unknown) => value === true || value === 1;
  if (data.rules.length > MAX_ROUTING_ITEMS) return `规则最多 ${MAX_ROUTING_ITEMS} 条，请删除多余规则。`;
  if (data.domainIds.length > MAX_ROUTING_ITEMS) return `接入域名最多 ${MAX_ROUTING_ITEMS} 个，请取消多余选择。`;
  if (!targets.some((target) => target.id === data.defaultTargetId && flagOn(target.enabled))) return '请选择已启用的默认目标；失效目标须重新选择。';
  if (!data.domainIds.length || data.domainIds.some((id) => !domains.some((domain) => domain.id === id && flagOn(domain.verified))))
    return '请选择至少一个已验证且仍可用的域名。';
  const priorities = new Set<number>();
  const ids = new Set<string>();
  for (const [index, rule] of data.rules.entries()) {
    const label = `规则 ${index + 1}：`;
    if (!/^[A-Za-z0-9_-]{1,32}$/.test(rule.id) || ids.has(rule.id)) return `${label}规则 ID 必须唯一且为不超过 32 位的 ASCII 标识符。`;
    ids.add(rule.id);
    const pathError = validateRoutingPath(rule.path);
    if (pathError) return label + pathError;
    if (!['EXACT', 'PREFIX'].includes(rule.matchType)) return `${label}请选择精确匹配或路径段前缀。`;
    if (!Number.isInteger(rule.priority) || rule.priority < 0 || rule.priority > MAX_ROUTING_PRIORITY || priorities.has(rule.priority))
      return `${label}优先级须为 0..1_000_000 范围内且站点内唯一的整数。`;
    priorities.add(rule.priority);
    if (rule.domainId && !data.domainIds.includes(rule.domainId)) return `${label}域名必须包含在接入域名中，请重新选择或清空为全部域名。`;
    if (!targets.some((target) => target.id === rule.targetId && flagOn(target.enabled))) return `${label}请选择已启用的目标。`;
  }
  return '';
}
