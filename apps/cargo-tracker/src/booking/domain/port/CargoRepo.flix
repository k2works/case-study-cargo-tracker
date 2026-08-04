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

        ///
        /// 旅程と経路状態を保存する（IT9・US09/US11）。
        ///
        /// **区間は置き換える**。経路の選び直しでは既存の行を消してから入れ直す——
        /// 足す形にすると、古い区間が混ざって連結の検証を通らない旅程ができる。
        ///
        /// **`updateStatus` と分ける**。旅程の割り当ては `BookingStatus` を
        /// 変えない操作であり（US13 との職掌の違い）、同じ関数にすると
        /// 「経路を割り当てたら確定した」という誤った実装を招きやすい。
        ///
        def saveItinerary(cargo: Cargo): Unit

        ///
        /// 旅程を含めて 1 件引く。
        ///
        /// **一覧では旅程を読まない**（`search` / `findAll`）。区間は別表であり、
        /// 一覧の各行で引くと N+1 になる。一覧に旅程は出さないため必要も無い。
        ///
        def findWithItinerary(bookingId: String): Option[Cargo]

        ///
        /// 予約を検索する。
        ///
        /// `shipperId` を与えると**その荷主の予約だけ**を返す。荷主ロールの
        /// 利用者が自分の予約だけを見るために使う（観点表の軸 6）。
        /// `None` なら全件が対象（営業担当者）。
        ///
        /// `criteria` は語・状態・荷主 ID の 3 軸（[CargoQuery](../model/Cargo.flix)）。
        /// **可視範囲（`shipperId`）とは別の引数**にしてある。同じ record に
        /// 畳むと、絞り込みを組み立てる場所で可視範囲を書き換えられてしまう。
        /// 可視範囲は利用者が触れない値であり、絞り込みは触れる値である。
        ///
        /// **`findAll` の結果をアプリ側で絞らない**。件数が増えるほど、
        /// 使われない行を DB から運んで捨てることになる（荷主一覧と同じ判断）。
        ///
        def search(shipperId: Option[String], criteria: BookingModel.CargoQuery): List[Cargo]
    }
}
