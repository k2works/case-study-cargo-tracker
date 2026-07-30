/**
 * 旅程スナップショット取得 ACL ポート（腐敗防止層）。
 * 追跡の推定到着日を Booking の旅程（leg の最終 unload_time）から取得するための
 * Tracking 固有の読み取り専用ポート。Booking のドメイン型には依存しない（BC 独立性・ADR-008 パターン）。
 */
export interface ItinerarySnapshotPort {
  /** 予約 ID に対応する旅程の最終荷降し時刻（推定到着日）を返す。旅程未確定なら null */
  findExpectedArrivalByBookingId(bookingId: string): Promise<Date | null>;
}
