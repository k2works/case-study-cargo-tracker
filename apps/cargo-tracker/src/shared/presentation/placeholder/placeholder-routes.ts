import { Role } from '../../domain/model/role.js';

/** プレースホルダ画面のルート定義（ui_design ロール別到達性マトリクス準拠） */
export interface PlaceholderRoute {
  path: string;
  title: string;
  roles: readonly Role[];
  storyNote: string;
}

export const PLACEHOLDER_ROUTES: readonly PlaceholderRoute[] = [
  { path: '/estimates', title: '見積管理', roles: [Role.SALES], storyNote: 'US01 輸送見積' },
  {
    path: '/bookings',
    title: '貨物予約一覧',
    roles: [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER],
    storyNote: 'US04 貨物予約',
  },
  {
    path: '/tracking',
    title: '貨物追跡入力',
    roles: [Role.SALES, Role.SHIPPER, Role.ROUTE_DESIGNER, Role.TRACKER],
    storyNote: 'US18 追跡照会',
  },
  {
    path: '/handling',
    title: '荷役作業一覧',
    roles: [Role.HANDLER, Role.TRACKER],
    storyNote: 'US15 荷役作業',
  },
  {
    path: '/voyages',
    title: '航路一覧',
    roles: [Role.ROUTE_DESIGNER],
    storyNote: 'US24 航海スケジュール',
  },
  {
    path: '/billing/invoices',
    title: '請求書一覧',
    roles: [Role.BILLING],
    storyNote: 'US21 請求・精算',
  },
];
