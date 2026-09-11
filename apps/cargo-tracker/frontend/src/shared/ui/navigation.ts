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
  // 追跡（S40）は追跡管理者と荷主。**荷主を外すと自社の貨物すら追えない**
  // （ui_design.md:234「追跡 | S40 | 追跡、荷主」）。
  { path: '/tracking', label: '追跡', allow: ['ROLE_TRACKER', 'ROLE_SHIPPER'] },
  // 例外（S42）は追跡管理者と**管理者**（ui_design.md の到達性の表 / IT11）。
  // **荷主には出さない**——起きていることは S41 で読めるが、対応するのは
  // 追跡管理者の仕事で、一覧を出しても打てる手が無い。
  //
  // **管理者を入れるのは US20 §受入基準 3 の読み口として。** 紛失を起票すると
  // 上位者へ知らせた記録が残るが、その記録を読む場所が無ければ「知らせた」
  // ことにならない。この一覧が緊急を先頭に出す。
  { path: '/tracking/exceptions', label: '例外', allow: ['ROLE_TRACKER', 'ROLE_ADMIN'] },
  // 荷役（S51）は荷役と追跡の両方（ui_design.md:236）。追跡管理者は
  // 問い合わせを受けたときに現場の記録を確かめる。
  { path: '/handling', label: '荷役', allow: ['ROLE_HANDLER', 'ROLE_TRACKER'] },
  // 通関（S52）は荷役と追跡の両方（ui_design.md:238）。荷役が申告を出し、
  // 追跡が状態を更新する。**どちらか一方にすると片方が入口を持たない。**
  { path: '/customs', label: '通関', allow: ['ROLE_HANDLER', 'ROLE_TRACKER'] },
  // 請求（S60）は経理だけ（ui_design.md の到達性の表）。**構成表には IT2 から
  // 載っていたが、実装側に入口が無かった**——設計が先にあり実装が追いついて
  // いない形（IT12 の S52 と逆）。US21 で画面ができたので開く。
  { path: '/invoices', label: '請求', allow: ['ROLE_ACCOUNTANT'] },
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
