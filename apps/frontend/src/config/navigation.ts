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
  { label: '貨物予約', to: '/booking', roles: ['ROLE_SALES', 'ROLE_SHIPPER'], available: false },
  { label: 'キャンセル承認', to: '/booking/cancellations', roles: ['ROLE_TRACKER'], available: false },
  { label: '航海スケジュール', to: '/routing/voyages', roles: ['ROLE_ROUTING'], available: false },
  { label: '経路設計', to: '/routing/design', roles: ['ROLE_ROUTING'], available: false },
  { label: '貨物追跡', to: '/tracking', roles: [], available: false },
  { label: '貨物状態管理', to: '/tracking/manage', roles: ['ROLE_TRACKER'], available: false },
  { label: '荷役管理', to: '/handling', roles: ['ROLE_HANDLER', 'ROLE_TRACKER'], available: false },
  { label: '通関管理', to: '/customs', roles: ['ROLE_HANDLER', 'ROLE_TRACKER'], available: false },
  { label: '精算管理', to: '/billing', roles: ['ROLE_ACCOUNTANT'], available: false },
]
