/**
 * 業務ロール。IT1 で 7 値に確定した（ui_design.md / domain-model.md と同一）。
 *
 * 経路設計者は営業担当者と別のアクターであり、兼務させると営業が航海スケジュール登録・
 * 経路確定まで行えてしまい職掌分離が崩れるため、独立したロールとする。
 */
export const ROLES = [
  'ROLE_SHIPPER',
  'ROLE_SALES',
  'ROLE_ROUTING',
  'ROLE_HANDLER',
  'ROLE_TRACKER',
  'ROLE_ACCOUNTANT',
  'ROLE_ADMIN',
] as const

export type Role = (typeof ROLES)[number]

/** 画面に表示するロールの呼び名。利用者は ROLE_SALES ではなく「営業担当者」で理解する。 */
export const ROLE_LABELS: Record<Role, string> = {
  ROLE_SHIPPER: '荷主',
  ROLE_SALES: '営業担当者',
  ROLE_ROUTING: '経路設計者',
  ROLE_HANDLER: '荷役作業員',
  ROLE_TRACKER: '追跡管理者',
  ROLE_ACCOUNTANT: '経理担当者',
  ROLE_ADMIN: '管理者',
}
