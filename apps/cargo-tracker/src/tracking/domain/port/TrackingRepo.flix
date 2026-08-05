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
        /// 輸送状態だけを更新する（IT10・US15）。
        ///
        /// **行全体を書き戻さない**。読み込んだ時点の古い値で他の列を
        /// 上書きしてしまう（`CargoRepo.updateStatus` と同じ判断）。
        ///
        def updateTransportStatus(trackingNumber: String,
                                  status: SharedTransportStatus.TransportStatus): Unit

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
