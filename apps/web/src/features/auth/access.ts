import type { Role } from '@/providers/AuthProvider';

/**
 * ロール × 画面の表（UI 設計 4.2・A8/F5/F6）。**この表が単一の出所**で、ナビゲーション・403 の判定・検査がすべてここから導かれる。
 * 画面を足したら、ここに足す（足さない画面は誰も開けない）。権限の根拠はバックエンドで、これは画面の出し分け。
 */
export type RouteAccess = { path: string; roles: readonly Role[] };

const SALES: readonly Role[] = ['Sales'];
const BOOKING_VIEWERS: readonly Role[] = ['Sales', 'RouteDesigner', 'TrackingManager'];
const EVERYONE: readonly Role[] = ['Shipper', 'Sales', 'RouteDesigner', 'TrackingManager', 'HandlingWorker', 'Accountant'];

export const ROUTE_ACCESS: readonly RouteAccess[] = [
  { path: '/', roles: EVERYONE },
  { path: '/shippers', roles: SALES },
  { path: '/shippers/new', roles: SALES },
  { path: '/shippers/:id', roles: SALES },
  { path: '/bookings', roles: BOOKING_VIEWERS },
  { path: '/bookings/new', roles: SALES },
  { path: '/bookings/:id', roles: BOOKING_VIEWERS },
];

export const canOpen = (path: string, role: Role): boolean => ROUTE_ACCESS.find((r) => r.path === path)?.roles.includes(role) ?? false;

/** メニューに出す項目（ホームは常に出す）。メニューに出す画面は、そのロールが開ける画面だけ。 */
const MENU: readonly { label: string; to: string }[] = [
  { label: 'ホーム', to: '/' },
  { label: '荷主', to: '/shippers' },
  { label: '予約', to: '/bookings' },
];

export const navFor = (role: Role): { label: string; to: string }[] => MENU.filter((m) => canOpen(m.to, role));
