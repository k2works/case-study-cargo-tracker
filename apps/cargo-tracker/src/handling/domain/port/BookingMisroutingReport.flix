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
        ///
        /// 経路逸脱を報告する。
        ///
        /// **追跡番号で渡す**（IT11・M13）。IT10 は予約番号を要求していたが、
        /// これは [ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)
        /// 決定 4「荷役は追跡番号で貨物を特定する」と食い違う。
        /// Handling が持っているのは貨物に貼られた追跡番号であり、
        /// 予約番号は**写しの中にたまたま入っていた**だけである。
        /// 予約番号への解決はアダプタの仕事——それが ACL の役目である。
        ///
        def report(trackingNumber: String): ReportResult
    }
}
