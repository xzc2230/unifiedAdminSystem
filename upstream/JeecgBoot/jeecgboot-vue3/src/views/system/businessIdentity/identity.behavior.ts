import type { LegacyTarget, LegacyPreview } from './identity.types';

export function validateLegacyId(value: unknown): string {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return '请输入有效的正整数历史账号编号';
  const max = '9223372036854775807';
  if (value.length > max.length || (value.length === max.length && value > max)) return '历史账号编号超出允许范围';
  return '';
}

export function validateReason(value: string): string {
  if (!value.trim()) return '请填写操作原因';
  return value.length > 500 ? '操作原因不能超过500个字符' : '';
}

export function hasConflict(value: unknown): boolean {
  if (Array.isArray(value)) return value.length > 0;
  if (value && typeof value === 'object') return Object.keys(value).length > 0;
  return !!value;
}

export function createRequestGate() {
  let version = 0;
  return {
    next: () => ++version,
    isCurrent: (request: number) => request === version,
    invalidate: () => {
      ++version;
    },
  };
}

export function createPreviewGuard() {
  const gate = createRequestGate();
  let accepted: { target: LegacyTarget; preview: LegacyPreview } | undefined;
  const key = (target: LegacyTarget) => JSON.stringify([target.tenantId, target.userId, target.legacyUserId]);
  return {
    begin(target: LegacyTarget) {
      accepted = undefined;
      return { version: gate.next(), target: { ...target } };
    },
    accept(request: { version: number; target: LegacyTarget }, preview: LegacyPreview) {
      if (!gate.isCurrent(request.version)) return false;
      accepted = { target: request.target, preview };
      return true;
    },
    invalidate() {
      gate.invalidate();
      accepted = undefined;
    },
    canBind(target: LegacyTarget) {
      return (
        !!accepted &&
        key(accepted.target) === key(target) &&
        !validateLegacyId(target.legacyUserId) &&
        accepted.preview.legacyUserId === target.legacyUserId &&
        accepted.preview.available === true &&
        !hasConflict(accepted.preview.conflict) &&
        !hasConflict(accepted.preview.ownershipConflicts)
      );
    },
  };
}

export function createSubmissionLock() {
  let locked = false;
  return {
    acquire() {
      if (locked) return false;
      locked = true;
      return true;
    },
    release() {
      locked = false;
    },
  };
}

export function errorText(error: unknown, fallback: string): string {
  const detail = error as { response?: { data?: { message?: unknown } }; message?: unknown } | null;
  const backend = detail?.response?.data?.message;
  if (typeof backend === 'string' && backend.trim()) return backend;
  return typeof detail?.message === 'string' && detail.message.trim() ? detail.message : fallback;
}
