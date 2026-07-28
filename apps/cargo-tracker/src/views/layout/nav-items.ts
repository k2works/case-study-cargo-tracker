import { Role } from '../../shared/domain/model/role.js';

/** ナビゲーション項目の定義 */
export interface NavItem {
  readonly label: string;
  readonly href: string;
  /** 表示を許可するロール（空配列は全ロール） */
  readonly roles: readonly Role[];
}

/**
 * navbar のメニュー項目。ui_design.md「ナビゲーション構成」および
 * 「ロール別画面到達性マトリクス」に準拠する。
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'ダッシュボード', href: '/', roles: [] },
  { label: '見積管理', href: '/estimates', roles: [Role.SALES] },
  {
    label: '荷主登録',
    href: '/shippers/new',
    roles: [Role.SALES],
  },
  {
    label: '貨物予約',
    href: '/bookings',
    roles: [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER],
  },
  {
    label: '貨物追跡',
    href: '/tracking',
    roles: [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER],
  },
  { label: '荷役管理', href: '/handling', roles: [Role.HANDLER, Role.TRACKER] },
  { label: '航路管理', href: '/voyages', roles: [Role.ROUTE_DESIGNER] },
  { label: '請求管理', href: '/billing/invoices', roles: [Role.BILLING] },
];

/** ユーザーのロールで表示可能な項目を絞り込む */
export function visibleNavItems(
  roles: readonly Role[],
): readonly NavItem[] {
  return NAV_ITEMS.filter(
    (item) => item.roles.length === 0 || item.roles.some((r) => roles.includes(r)),
  );
}
