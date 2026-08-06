/**
 * システムの認可ロール（RBAC 6 ロール）。
 * data-model.md `user_roles.role` の 6 値に一致する。
 */
export const Role = {
  SHIPPER: 'ROLE_SHIPPER',
  SALES: 'ROLE_SALES',
  ROUTE_DESIGNER: 'ROLE_ROUTE_DESIGNER',
  TRACKER: 'ROLE_TRACKER',
  HANDLER: 'ROLE_HANDLER',
  BILLING: 'ROLE_BILLING',
} as const;

export type Role = (typeof Role)[keyof typeof Role];

export const ALL_ROLES: readonly Role[] = Object.values(Role);

/** ロールの日本語表示名 */
export const ROLE_LABELS: Record<Role, string> = {
  ROLE_SHIPPER: '荷主',
  ROLE_SALES: '営業担当者',
  ROLE_ROUTE_DESIGNER: '経路設計者',
  ROLE_TRACKER: '追跡管理者',
  ROLE_HANDLER: '荷役作業員',
  ROLE_BILLING: '経理担当者',
};

/** 文字列が有効な Role か判定する */
export function isRole(value: string): value is Role {
  return (ALL_ROLES as readonly string[]).includes(value);
}
