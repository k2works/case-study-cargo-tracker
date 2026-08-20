import type { Role } from '../types/role'

/**
 * ロール別のダッシュボード構成。
 *
 * 入口のみを置き、要対応件数は各ストーリーの実装時に足す。
 * コンポーネントと同じファイルに置くと Fast Refresh が効かなくなるため分けている。
 */

type Panel = {
  role: Role
  title: string
  /** その担当が「次に何をするか」への入口。件数だけ出しても仕事は進まない。 */
  actions: { label: string; to: string }[]
}

/**
 * ロールごとの作業入口。
 *
 * ここに並べたリンクは、そのロールで実際に開けなければならない。開けない画面へ誘導すると、
 * 押した先で断られる。到達性は dashboard-page.test.tsx が検査する。
 */
export const PANELS: Panel[] = [
  {
    role: 'ROLE_SALES',
    title: '営業ダッシュボード',
    actions: [
      { label: '荷主を登録する', to: '/booking/shippers/new' },
      { label: '荷主を探す', to: '/booking/shippers' },
      { label: '貨物予約を登録する', to: '/booking/new' },
      { label: '貨物予約を見る', to: '/booking' },
    ],
  },
  {
    role: 'ROLE_SHIPPER',
    title: '荷主ダッシュボード',
    // 貨物予約は営業担当者の画面であり、荷主ロールは 403 になる（ADR-008）。
    // 開いていない画面へ誘導すると、押した先で断られる
    actions: [{ label: '自分の貨物の状況を見る', to: '/tracking' }],
  },
  {
    role: 'ROLE_ROUTING',
    title: '経路設計ダッシュボード',
    actions: [{ label: '航海スケジュールを見る', to: '/routing/voyages' }],
  },
  {
    role: 'ROLE_HANDLER',
    title: '荷役ダッシュボード',
    actions: [{ label: '荷役作業を記録する', to: '/handling' }],
  },
  {
    role: 'ROLE_TRACKER',
    title: '追跡管理ダッシュボード',
    actions: [
      { label: '貨物の状態を確認する', to: '/tracking/manage' },
      { label: 'キャンセル申請を確認する', to: '/booking/cancellations' },
    ],
  },
  {
    role: 'ROLE_ACCOUNTANT',
    title: '経理ダッシュボード',
    actions: [{ label: '請求書を確認する', to: '/billing' }],
  },
]
