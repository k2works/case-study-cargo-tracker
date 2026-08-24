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
      // 引き渡し忘れは、予約が増えるほど一覧を見ても気づけなくなる（#553）
      { label: 'まだ依頼していない予約を見る', to: '/booking?routingStatus=NOT_ROUTED' },
      // 荷主と条件を話せるのは営業だけ。気づかないと予約が止まったままになる
      {
        label: '条件の協議を求められている予約を見る',
        to: '/booking?routingStatus=CONSULTATION_REQUESTED',
      },
      // 経路が決まったことは営業には何も知らされない（メールの仕組みが無い）。
      // 一覧の「経路」列は通知前も通知後も「経路確定」のままで、そこからは分けられない
      { label: '荷主へ通知していない予約を見る', to: '/booking?bookingStatus=ROUTE_PROPOSED' },
      // 督促するかどうかは「いつ通知したか」で決める。放っておくと予約が止まる
      { label: '荷主の返事を待っている予約を見る', to: '/booking?bookingStatus=ROUTE_NOTIFIED' },
      // 番号を発行するのは経路設計者、伝えるのは営業。知らされないと伝え忘れる
      {
        label: '追跡番号を荷主へ伝える予約を見る',
        to: '/booking?bookingStatus=TRACKING_ISSUED',
      },
      // 荷主は公開の追跡照会で「ご依頼元の営業担当へ」と案内される。営業に気づく手段が
      // 無いと、電話を受けてから追跡管理者を探すことになる（IT9 返済枠 0.9）
      { label: '例外が起きている貨物を見る', to: '/tracking/manage/exceptions' },
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
    actions: [
      // 件数だけ出しても仕事は進まない。ここから対象の一覧へ行けるようにする（US06）
      { label: '経路設計を待っている予約を見る', to: '/booking?routingStatus=ROUTING_REQUESTED' },
      // 確定した予約は経路設計者が追跡番号を発行する（US13-3・US14）。
      // 件数だけ出しても仕事は進まない。ここから対象の一覧へ行けるようにする
      { label: '追跡番号の発行を待っている予約を見る', to: '/booking?bookingStatus=CONFIRMED' },
      { label: '航海スケジュールを見る', to: '/routing/voyages' },
      // 経路設計（/routing/design/:bookingId）はここに置かない。予約を選ばないと
      // 開けない画面であり、ダッシュボードから直接踏むと予約番号の無い URL になる。
      // 経路設計者は「経路設計を待っている予約」→ 予約詳細 → [経路を割り当て] と辿る
    ],
  },
  {
    role: 'ROLE_HANDLER',
    title: '荷役ダッシュボード',
    actions: [
      { label: '荷役作業を記録する', to: '/handling' },
      // **破損・紛失に最初に気づくのは港にいる人である**（US20 のアクターは 2 つ）。
      // 気づいた人が伝える手段を持たないと、例外はどこにも起票されない
      { label: '貨物の破損・紛失を報告する', to: '/tracking/manage' },
    ],
  },
  {
    role: 'ROLE_TRACKER',
    title: '追跡管理ダッシュボード',
    actions: [
      // 荷役の結果を見る唯一の入口（US15 の履歴）。追跡管理者の担当画面は他がまだ
      // 準備中で、ここが無いと「使える画面が 1 つも無い」と案内されることになる
      { label: '貨物の作業履歴を見る', to: '/handling' },
      { label: '貨物の状態を管理する', to: '/tracking/manage' },
      // **件数を出すだけにしない。**気づく手段は次の行動へ繋ぐ（横断規約）。
      // 未解決の例外は放っておくと荷主の問い合わせになって返ってくる
      { label: '未解決の例外を見る', to: '/tracking/manage/exceptions' },
      { label: 'キャンセル申請を確認する', to: '/booking/cancellations' },
    ],
  },
  {
    role: 'ROLE_ADMIN',
    title: '管理者ダッシュボード',
    // ロックされた利用者は自分では何もできない。管理者がそこへ行けないと業務が止まる
    actions: [{ label: 'ロックされたアカウントを解除する', to: '/admin/accounts' }],
  },
  {
    role: 'ROLE_ACCOUNTANT',
    title: '経理ダッシュボード',
    actions: [{ label: '請求書を確認する', to: '/billing' }],
  },
]
