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

        ///
        /// 既存の貨物の**状態だけ**を更新する。
        ///
        /// `save`（登録）と分けるのは、**登録と更新を 1 つの操作にまとめると
        /// 「更新したつもりが新規に入る」「登録したつもりが既存を潰す」が
        /// 静かに起きる**ためである。呼び出し側はどちらの意図かを常に持っている。
        ///
        /// **名前に `Status` を含める**。汎用の `update` にすると、
        /// 「予約内容の訂正」で呼んだときに変更が静かに消える
        /// （実装は状態列しか書かない。行全体を書き戻すと、同時に別の更新が
        /// あったとき読み込んだ時点の古い値で他の列を上書きしてしまう）。
        ///
        def updateStatus(cargo: Cargo): Unit
    }
}
