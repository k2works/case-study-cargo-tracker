import type { Role } from '../auth/roles';

/**
 * サイドナビの項目。
 *
 * <p>ここが画面の到達性の正典になる。画面を足したのにここに書き忘れると、
 * 受入基準を満たしていても誰もその画面に行けない。ルート定義とこの表を
 * 同じ変更で直す。</p>
 */
export interface NavigationItem {
  readonly path: string;
  readonly label: string;
  readonly allow: readonly Role[];
}

export const NAVIGATION: readonly NavigationItem[] = [
  { path: '/', label: 'ダッシュボード', allow: ['ROLE_SHIPPER', 'ROLE_SALES', 'ROLE_ROUTING', 'ROLE_TRACKER', 'ROLE_HANDLER', 'ROLE_ACCOUNTANT', 'ROLE_ADMIN'] },
  { path: '/shippers', label: '荷主一覧', allow: ['ROLE_SALES', 'ROLE_ACCOUNTANT'] },
  { path: '/shippers/new', label: '荷主登録', allow: ['ROLE_SALES'] },
  { path: '/worklist/attention', label: '要確認一覧', allow: ['ROLE_SALES', 'ROLE_ACCOUNTANT', 'ROLE_TRACKER'] },
];

export function navigationFor(roles: readonly Role[]): readonly NavigationItem[] {
  return NAVIGATION.filter((item) => item.allow.some((role) => roles.includes(role)));
}
