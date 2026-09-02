/** ロール（domain-model.md の Role と 1:1）。増やしたらここと画面の到達性検査を同時に直す。 */
export const ROLES = [
  'ROLE_SHIPPER',
  'ROLE_SALES',
  'ROLE_ROUTING',
  'ROLE_TRACKER',
  'ROLE_HANDLER',
  'ROLE_ACCOUNTANT',
  'ROLE_ADMIN',
] as const;

export type Role = (typeof ROLES)[number];

/** 画面に出すときの呼び名。 */
export const ROLE_LABELS: Record<Role, string> = {
  ROLE_SHIPPER: '荷主',
  ROLE_SALES: '営業担当者',
  ROLE_ROUTING: '経路設計者',
  ROLE_TRACKER: '追跡管理者',
  ROLE_HANDLER: '荷役担当者',
  ROLE_ACCOUNTANT: '経理担当者',
  ROLE_ADMIN: '管理者',
};

export function isRole(value: string): value is Role {
  return (ROLES as readonly string[]).includes(value);
}
