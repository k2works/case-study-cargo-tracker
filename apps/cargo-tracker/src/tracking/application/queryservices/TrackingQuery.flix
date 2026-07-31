///
/// 追跡情報のクエリサービス（アプリケーション層）。
///
/// `ReadDb` 効果を要求するだけで、その実装（JDBC / インメモリ）は知らない。
/// ハンドラの差し替えだけでテストダブルに置き換えられる（ADR-0002）。
///
mod TrackingQuery {
    use TrackingReadDb.ReadDb
    use TrackingView.TrackingDetailView

    ///
    /// 追跡番号で追跡情報を取得する。
    ///
    /// 見つからない場合は `None` を返す。エラーではなく「未検出」は正常系として扱い、
    /// 画面側でメッセージを表示する（US18 の受入基準）。
    ///
    pub def findByTrackingNumber(trackingNumber: String): Option[TrackingDetailView] \ ReadDb =
        ReadDb.findByTrackingNumber(trackingNumber)
}
