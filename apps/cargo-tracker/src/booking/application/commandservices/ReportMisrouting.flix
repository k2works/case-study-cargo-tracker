///
/// 経路逸脱の記録（US15。IT10）。
///
/// **Handling が呼ぶ**（`composition/acl/` のアダプタ経由）。
/// 呼び出し元が誰であっても、`BookingModel.markMisrouted` を通る限り
/// 壊れた状態は入らない（ADR-0012）。
///
mod BookingReportMisrouting {
    use BookingModel.BookingError
    use CargoRepo.CargoRepo

    pub enum ReportResult with Eq, ToString {
        case Reported
        case NotFound
        case Rejected(BookingError)
    }

    ///
    /// 経路逸脱を記録する。
    ///
    /// **`findForUpdate` で行を押さえてから読む**（IT9 レビュー H2 と同じ理由）。
    /// 経路の割り当てと交錯すると、逸脱の記録が古い経路に対して行われる。
    ///
    /// **`updateRoutingStatus` で書く**（IT11・M4）。IT10 は `saveItinerary` を
    /// 使っており、状態を 1 つ変えるだけの操作が**旅程の全削除・再挿入**を
    /// 通っていた。同時に経路が割り当てられていると区間を消し合う。
    /// `updateStatus` は `BookingStatus` しか書かないため使えない。
    ///
    pub def report(bookingId: String): ReportResult \ CargoRepo =
        match CargoRepo.findForUpdate(bookingId) {
            case None    => ReportResult.NotFound
            case Some(c) =>
                match BookingModel.markMisrouted(c) {
                    case Err(e)      => ReportResult.Rejected(e)
                    case Ok(updated) => {
                        CargoRepo.updateRoutingStatus(updated);
                        ReportResult.Reported
                    }
                }
        }
}
