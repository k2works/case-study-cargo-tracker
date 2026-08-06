/** 対応報告通知の本文材料（US19-4/5・US20-5。ADR-012） */
export interface ExceptionReportDetail {
  /** 新到着予定日（遅延時。任意） */
  newEstimatedArrival: Date | null;
  /** 対応方針・補償方針 */
  notes: string;
}

/**
 * 荷主・管理職への通知ポート（US17 状態変更・US19/US20 例外通知）。
 * 宛先解決（荷主メール等）と送信記録の登録はインフラアダプタが担う。
 * コミット後副作用のため、失敗はコマンド失敗として扱わない（ADR-009）。
 */
export interface TrackingNotificationPort {
  /** 状態変更を荷主へ通知する（US17） */
  notifyStatusChange(bookingId: string, status: string): Promise<void>;

  /** 例外発生を荷主へ通知する（US19-3・US20-4） */
  notifyException(bookingId: string, exceptionType: string): Promise<void>;

  /**
   * 例外を管理職へエスカレーション通知する（US20-3・LOST のみ）。
   * 本文に例外種別・発生地の要約を載せる（ADR-012）。
   */
  notifyEscalation(bookingId: string, exceptionType: string, location: string): Promise<void>;

  /**
   * 対応報告を荷主へ送信する（US19-4/5・US20-5）。
   * 本文に新到着予定日・対応方針の要約を載せる（ADR-012）。
   */
  notifyExceptionReport(
    bookingId: string,
    exceptionType: string,
    detail: ExceptionReportDetail,
  ): Promise<void>;
}
