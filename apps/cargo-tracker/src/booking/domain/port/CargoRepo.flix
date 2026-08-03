///
/// 貨物リポジトリのポート（出力ポート）。
///
mod CargoRepo {
    use BookingModel.Cargo

    ///
    /// 貨物の永続化と検索。
    ///
    pub eff CargoRepo {
        /// 貨物を保存する
        def save(cargo: Cargo): Unit

        /// 予約番号で貨物を引く
        def findByBookingId(bookingId: String): Option[Cargo]

        /// 貨物を一覧する
        def findAll(): List[Cargo]
    }
}
