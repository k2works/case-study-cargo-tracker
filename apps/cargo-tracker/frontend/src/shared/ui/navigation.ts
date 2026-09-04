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
  { path: '/bookings', label: '予約一覧', allow: ['ROLE_SALES', 'ROLE_ROUTING', 'ROLE_TRACKER'] },
  { path: '/bookings/new', label: '予約登録', allow: ['ROLE_SALES'] },
  { path: '/routing/worklist', label: '経路設計作業', allow: ['ROLE_ROUTING'] },
  { path: '/voyages', label: '航海スケジュール', allow: ['ROLE_ROUTING'] },
  { path: '/voyages/new', label: '航海登録', allow: ['ROLE_ROUTING'] },
  {
    path: '/worklist/attention',
    label: '要確認一覧',
    // 経路設計者も宛先になる。航海の投影が一意制約で弾いた事実は
    // ROLE_ROUTING 宛に記録されるので、ここに入れないと本人が開けない。
    allow: ['ROLE_SALES', 'ROLE_ACCOUNTANT', 'ROLE_TRACKER', 'ROLE_ROUTING'],
  },
  { path: '/admin/users', label: '利用者管理', allow: ['ROLE_ADMIN'] },
];

/**
 * SPA の外にある資料への導線（ドキュメントポータル）。
 *
 * <p>ロールで出し分けない。設計・手順書・マニュアルは職掌に関わらず
 * 読めてよく、隠すと「どこかにあるらしい」状態のまま問い合わせになる。</p>
 *
 * <p>絶対 URL を焼き込まないのは、環境ごとにホストが変わるため。nginx
 * （本番相当）と Vite（開発）が同じパスでポータルへ中継する。</p>
 */
export interface ExternalLink {
  readonly href: string;
  readonly label: string;
}

export const EXTERNAL_LINKS: readonly ExternalLink[] = [
  { href: '/docs-portal/', label: 'ドキュメント' },
  { href: '/docs-portal/manual/', label: 'マニュアル' },
];

export function navigationFor(roles: readonly Role[]): readonly NavigationItem[] {
  return NAVIGATION.filter((item) => item.allow.some((role) => roles.includes(role)));
}
