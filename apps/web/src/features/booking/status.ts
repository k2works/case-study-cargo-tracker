/** 予約状態（要件定義書の状態モデルを正とする。R-06）の表示名。**この表が単一の出所**。 */
const LABELS = {
  RECEIVED: '仮受付',
  ROUTE_PROPOSAL: '経路提案中',
  CONFIRMED: '予約確定',
  TRACKING_ISSUED: '追跡番号発行済',
  IN_TRANSIT: '輸送中',
  DELIVERED: '配送完了',
  SETTLED: '精算済',
  CANCELLED: 'キャンセル',
} as const;

export type BookingStatus = keyof typeof LABELS;
export const BOOKING_STATUSES = Object.keys(LABELS) as BookingStatus[];

/** 未知の状態は、そのまま見せず「不明な状態」にする（サーバーが新しい状態を足したときに気づける）。 */
export const statusLabel = (status: string): string => (status in LABELS ? LABELS[status as BookingStatus] : '不明な状態');

const HISTORY_LABELS: Record<string, string> = {
  REGISTERED: '登録',
  CORRECTED: '修正',
  ROUTE_DESIGN_REQUESTED: '経路設計の依頼',
  ROUTE_ATTACHED: '経路の紐付け',
  ROUTE_DETACHED: '経路の解除',
  CONFIRMED: '予約確定',
  TRACKING_ISSUED: '追跡番号の発行',
  TRANSIT_STARTED: '輸送開始',
  ROUTE_REPLACED: '経路の差し替え',
  DELIVERED: '配送完了',
  SETTLED: '精算済',
  CANCELLED: 'キャンセル',
  CANCELLATION_REQUESTED: 'キャンセルの申請',
  CANCELLATION_APPROVED: 'キャンセルの承認',
  CANCELLATION_REJECTED: 'キャンセルの却下',
};
export const historyLabel = (kind: string): string => HISTORY_LABELS[kind] ?? '不明な履歴';
