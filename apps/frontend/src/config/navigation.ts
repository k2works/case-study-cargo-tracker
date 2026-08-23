import type { Role } from '../types/role'

export type NavigationItem = {
  label: string
  to: string
  /** 表示するロール。空配列は全ロール（認証済み）を意味する。 */
  roles: Role[]
  /**
   * その画面が使えるか。
   *
   * 未実装の画面をリンクのまま出すと、押してもどこにも行けず「壊れている」と受け取られる。
   * 実装したイテレーションで true にする。
   */
  available: boolean
}

/**
 * サイドバーのナビゲーション定義（ui_design.md のナビゲーション構成に一致させる）。
 *
 * 画面を作っても、そのロールがここから到達できなければ利用者の仕事は進まない。
 * 画面を追加したら必ずここにも足す。
 */
export const NAVIGATION: NavigationItem[] = [
  { label: 'ダッシュボード', to: '/dashboard', roles: [], available: true },
  { label: '荷主管理', to: '/booking/shippers', roles: ['ROLE_SALES'], available: true },
  { label: '見積管理', to: '/booking/estimates', roles: ['ROLE_SALES'], available: false },
  // ROLE_SHIPPER には開かない（ADR-008）。利用者と荷主を結ぶキーが無く
  // 「自分の予約だけ」に絞り込めないため、開くと全荷主の予約が見える。US18（IT8）で広げ直す
  // 経路設計者にも開く。引き渡された予約へ行けないと、依頼に気づいても仕事が進まない。
  // 見える範囲は依頼済みだけで、絞り込みの指定では広げられない（ADR-015）
  { label: '貨物予約', to: '/booking', roles: ['ROLE_SALES', 'ROLE_ROUTING'], available: true },
  { label: 'キャンセル承認', to: '/booking/cancellations', roles: ['ROLE_TRACKER'], available: false },
  { label: '航海スケジュール', to: '/routing/voyages', roles: ['ROLE_ROUTING'], available: true },
  // 経路設計（/routing/design/:bookingId）はサイドバーに置かない。
  // 予約を選ばないと開けない画面であり、メニューから踏むと予約番号の無い URL になる。
  // 入口は予約詳細の [経路を割り当て] で、経路設計者は「経路設計待ち」の予約一覧から辿る
  { label: '貨物追跡', to: '/tracking', roles: [], available: false },
  { label: '貨物状態管理', to: '/tracking/manage', roles: ['ROLE_TRACKER'], available: false },
  // 記録できるのは荷役作業員だけだが、参照は追跡管理者にも開く（US15 の履歴）。
  // メニューに出すのは、そのロールで**何かできる**画面に限る
  { label: '荷役管理', to: '/handling', roles: ['ROLE_HANDLER', 'ROLE_TRACKER'], available: true },
  { label: '通関管理', to: '/customs', roles: ['ROLE_HANDLER', 'ROLE_TRACKER'], available: false },
  { label: '精算管理', to: '/billing', roles: ['ROLE_ACCOUNTANT'], available: false },
  // ロックされたアカウントの解除（US32）。**管理者以外には出さない**——出すと、
  // 押した先で 403 になる画面へ誘導することになる
  { label: 'アカウント管理', to: '/admin/accounts', roles: ['ROLE_ADMIN'], available: true },
]

/**
 * その URL を担当するメニューを返す。
 *
 * 前方一致で最初に当たったものを使ってはいけない。/booking/cancellations が
 * /booking に吸われ、準備中の画面が「使える」と判定される。最も長く一致した
 * ものが、その URL を担当するメニューである。
 *
 * この判定はダッシュボードと検査の両方から使う。別々に書くと、検査だけが
 * 正しく判定して本番の誤りを素通りさせる（IT2 で実際に起きた）。
 */
export function resolveNavigationItem(to: string): NavigationItem | undefined {
  return NAVIGATION.filter((item) => item.to !== '/' && to.startsWith(item.to)).sort(
    (a, b) => b.to.length - a.to.length,
  )[0]
}
