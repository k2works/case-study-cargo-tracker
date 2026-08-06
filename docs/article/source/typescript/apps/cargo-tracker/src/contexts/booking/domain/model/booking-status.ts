/** 予約状態（9 値。IT2 では PRELIMINARY / ROUTING_IN_PROGRESS / CANCELLED を扱う） */
export const BookingStatus = {
  PRELIMINARY: 'PRELIMINARY',
  ROUTING_IN_PROGRESS: 'ROUTING_IN_PROGRESS',
  ROUTE_PROPOSED: 'ROUTE_PROPOSED',
  CONFIRMED: 'CONFIRMED',
  TRACKING_ISSUED: 'TRACKING_ISSUED',
  IN_TRANSIT: 'IN_TRANSIT',
  DELIVERED: 'DELIVERED',
  SETTLED: 'SETTLED',
  CANCELLED: 'CANCELLED',
} as const;
export type BookingStatus = (typeof BookingStatus)[keyof typeof BookingStatus];

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  PRELIMINARY: '仮受付',
  ROUTING_IN_PROGRESS: '経路設計中',
  ROUTE_PROPOSED: '経路提案中',
  CONFIRMED: '確定',
  TRACKING_ISSUED: '追跡発行済',
  IN_TRANSIT: '輸送中',
  DELIVERED: '配達済',
  SETTLED: '精算済',
  CANCELLED: 'キャンセル',
};

export function isBookingStatus(value: string): value is BookingStatus {
  return Object.values(BookingStatus).includes(value as BookingStatus);
}
