///
/// 経路逸脱の報告（ACL ポート。US15・IT10）。
///
/// **Handling の語彙だけで定義する**。`Cargo` も `RoutingStatus` も現れない。
///
/// 実装は**合成ルートの `acl/`** が与え、Booking の集約
/// （`Cargo.markMisrouted`）を必ず通す（ADR-0012）。
///
/// # なぜ Handling から報告するか
///
/// 「予定と違う船に乗った」ことを知っているのは荷役の側である。
/// Booking が定期的に確かめる形にすると、**気付くのが遅れる**——
/// 経路設計者が作り直す時間が減る。
///
mod HandlingBookingMisroutingReport {

    pub enum ReportResult with Eq, ToString {
        case Reported
        /// 予約が見つからない
        case BookingNotFound
        /// 予約が経路を持たない（報告する対象が無い）
        case NotRouted
    }

    pub eff BookingMisroutingReport {
        /// 経路逸脱を予約へ報告する
        def report(bookingId: String): ReportResult
    }
}
