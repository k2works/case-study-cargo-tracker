/**
 * 荷主連絡先解決ポート（US17 通知宛先・IT6 Try T4）。
 * 通知アダプタが cargo × shipper を生 JOIN する代わりに、予約 ID から荷主メールを
 * 解決する責務をポート裏へ隠蔽する（Booking の ShipperContactAcl と同型）。
 */
export interface ShipperContactPort {
  /** 予約 ID から荷主の通知先メールを解決する。未解決時は null */
  findEmailByBookingId(bookingId: string): Promise<string | null>;
}
