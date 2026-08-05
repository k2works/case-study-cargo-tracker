///
/// 経路逸脱の報告の翻訳（Handling → Booking の ACL アダプタ。IT10・US15）。
///
/// **BC 間の翻訳の唯一の置き場所**である（`arch-lint` 規約 11・ADR-0012）。
///
/// Booking のアプリケーションサービスを呼び、集約（`Cargo.markMisrouted`）を通す。
/// 「経路が割り当てられているか」は集約が決める——
/// **ここで検証を書くと、集約の不変条件が 2 箇所になる**。
///
mod BookingMisroutingReportAdapter {
    use CargoRepo.CargoRepo
    use HandlingBookingMisroutingReport.{BookingMisroutingReport, ReportResult}

    /// 本アダプタが要求する効果（配線ファイルに Booking の効果名を書かせない）
    pub type alias Requires = CargoRepo

    pub def withBookingMisroutingReport(f: Unit -> a \ ef + BookingMisroutingReport):
            a \ (ef - BookingMisroutingReport) + Requires =
        run {
            f()
        } with handler BookingMisroutingReport {
            def report(trackingNumber, k) = k(reportInBooking(trackingNumber))
        }

    ///
    /// 追跡番号から予約を解決して報告する（IT11・M13）。
    ///
    /// **解決はここで行う**。Handling が持っているのは追跡番号だけであり
    /// （ADR-0012 決定 4）、予約番号は Booking の語彙である。
    /// 識別子の翻訳こそ ACL の役目である。
    ///
    def reportInBooking(trackingNumber: String): ReportResult \ CargoRepo =
        match CargoRepo.findByTrackingNumber(trackingNumber) {
            case None    => ReportResult.BookingNotFound
            case Some(c) => reportFor(BookingModel.bookingIdOf(c))
        }

    def reportFor(bookingId: String): ReportResult \ CargoRepo =
        match BookingReportMisrouting.report(bookingId) {
            case BookingReportMisrouting.ReportResult.Reported   => ReportResult.Reported
            case BookingReportMisrouting.ReportResult.NotFound   => ReportResult.BookingNotFound
            case BookingReportMisrouting.ReportResult.Rejected(_) => ReportResult.NotRouted
        }
}
