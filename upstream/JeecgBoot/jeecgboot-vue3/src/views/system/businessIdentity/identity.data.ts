import type { BasicColumn } from '/@/components/Table';

export const stateLabels = { UNOPENED: '未开通', ACTIVE: '已开通', DISABLED: '已停用', ANOMALOUS: '映射异常' };
export const stateColors = { UNOPENED: 'default', ACTIVE: 'success', DISABLED: 'warning', ANOMALOUS: 'error' };
export const stateOptions = Object.entries(stateLabels).map(([value, label]) => ({ value, label }));
export const stateLabel = (value: string | null) => (value ? stateLabels[value] || value : '—');
export const resourceLabels: Record<string, string> = {
  pkg_app_manager: '应用',
  pkg_app_statistics: '应用统计',
  pkg_load_count: '落地页统计',
  pkg_pwa: 'PWA',
  pkg_notification: '通知',
  pkg_app_event: '应用事件',
};
export const columns: BasicColumn[] = [
  { title: '用户名', dataIndex: 'username', width: 150 },
  { title: '姓名', dataIndex: 'realname', width: 120 },
  { title: '租户', dataIndex: 'tenantName', width: 160 },
  { title: '业务账号编号', dataIndex: 'legacyUserId', width: 200, customRender: ({ text }) => text || '—' },
  { title: '业务账号状态', dataIndex: 'identityState', width: 120, slots: { customRender: 'state' } },
  { title: '用户状态', dataIndex: 'userStatus', width: 100, customRender: ({ text }) => (String(text) === '1' ? '正常' : '不可用') },
  { title: '成员状态', dataIndex: 'memberStatus', width: 100, customRender: ({ text }) => (String(text) === '1' ? '正常' : '不可用') },
  { title: '租户状态', dataIndex: 'tenantActive', width: 100, customRender: ({ text }) => (text === true ? '有效' : '不可用') },
  {
    title: '来源',
    dataIndex: 'source',
    width: 120,
    customRender: ({ text }) => ({ NEW: '新开户', LEGACY: '历史绑定', MIGRATED: '历史迁移' })[text] || text || '—',
  },
];

export const auditColumns: BasicColumn[] = [
  { title: '时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作人', dataIndex: 'operatorName', width: 120 },
  { title: '操作人ID', dataIndex: 'operatorId', width: 160 },
  {
    title: '操作',
    dataIndex: 'action',
    width: 100,
    customRender: ({ text }) => ({ OPEN: '开户', BIND: '历史绑定', ENABLE: '恢复', DISABLE: '停用', STATUS: '状态变更' })[text] || text,
  },
  { title: '业务账号编号', dataIndex: 'legacyUserId', width: 200 },
  { title: '变更前', dataIndex: 'beforeState', width: 100, customRender: ({ text }) => stateLabel(text) },
  { title: '变更后', dataIndex: 'afterState', width: 100, customRender: ({ text }) => stateLabel(text) },
  { title: '操作原因', dataIndex: 'reason', width: 260, ellipsis: false },
  { title: '记录ID', dataIndex: 'id', width: 160 },
];
