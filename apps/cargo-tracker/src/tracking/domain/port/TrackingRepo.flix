///
/// 追跡活動の永続化ポート（書き込み側）。
///
/// **`ReadDb` と別効果にする**。`ReadDb` は CQRS のクエリ側で、集約を再構築せず
/// 画面表示用の Read Model を返す。書き込みを同じ効果に混ぜると、
/// 「クエリサービスが誤って書き込む」ことを型で防げなくなる
/// （[バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)の CQRS 適用方針）。
///
mod TrackingRepo {
    use TrackingModel.TrackingActivity

    pub eff TrackingRepo {
        /// 追跡活動を保存する（新規）
        def save(activity: TrackingActivity): Unit

        /// 予約番号で引く。**発行済みかどうかの判定**に使う
        def findByBookingId(bookingId: String): Option[TrackingActivity]

        /// 追跡番号で引く（IT10・US15。荷役の反映先を確かめる）
        def findByTrackingNumber(trackingNumber: String): Option[TrackingActivity]

        ///
        /// 荷役の反映を保存する（IT10・US15。IT11 で出来事も書くようにした）。
        ///
        /// **集約を引数に取る**。`(trackingNumber, status)` の 2 値を受ける形にすると、
        /// **ポートの型そのものが集約を迂回する呼び出しを合法にする**——
        /// IT10 のレビューで実際にそうなっていた。書き込みは
        /// `TrackingModel.applyHandling` を通ったものだけを受け取る。
        ///
        /// 書くのは**輸送状態の列と、集約が抱えている未書き込みの出来事**である。
        /// 行全体を書き戻すと、読み込んだ時点の古い値で他の列を上書きしてしまう。
        ///
        /// 状態と出来事を**同じメソッドで書く**のは、片方だけが書かれた状態を
        /// 呼び出し側が作れないようにするためである。IT10 では状態しか書かれず、
        /// 荷主のタイムラインが空のままだった（H8）。
        ///
        def saveHandling(activity: TrackingActivity): Unit

        ///
        /// 追跡番号で引き、**行を押さえる**（`SELECT ... FOR UPDATE`）。
        ///
        /// 荷役の同時登録（積込と荷降しをほぼ同時に登録する・二重送信）では、
        /// 押さえないと輸送状態が**後着順**で決まる。積込の後に遅れて到着した
        /// 受領が上書きすると、輸送中の貨物が受取済に見える。
        ///
        def findForUpdate(trackingNumber: String): Option[TrackingActivity]

        ///
        /// その日の次の連番を返す（`TRK-YYYYMMDD-NNNN` の `NNNN`）。
        ///
        /// **ここが採番の競合点である**。同じ日に 2 人が同時に発行すると、
        /// 両方が同じ「次」を読みうる。
        ///
        /// 本ポートは連番を返すだけで、一意性を保証しない。**保証するのは
        /// DB の UNIQUE 制約**（`tracking_activity.tracking_number` と
        /// `cargo.tracking_number`）である——アプリケーション側の
        /// 「読む → 決める → 書く」は、その 3 つの間に別のトランザクションが
        /// 書けば必ず破れる（ADR-0008 と同じ理屈）。
        ///
        /// 制約を外すと `TrackingNumberHttpTest` の同時発行テストが赤くなる
        /// ことを実測している（IT9 ふりかえり Try T3）。
        ///
        def nextSequenceFor(isoDate: String): Int32
    }
}
