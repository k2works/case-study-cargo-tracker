///
/// Tracking Context の読み取り出力ポート（CQRS のクエリ側）。
///
/// **効果として宣言する**ことで、実装（JDBC / インメモリ）をハンドラで注入する。
/// 書き込み用ポートとは別効果にすることで「クエリサービスが誤って書き込む」ことを
/// 型レベルで防ぐ（docs/design/architecture_backend.md の CQRS 適用方針）。
///
/// ドメイン層に属するため、Java 相互運用・インフラ層への参照を持たない（arch-lint 規約 1・2）。
///
mod TrackingReadDb {
    use TrackingView.TrackingDetailView

    ///
    /// 追跡情報の読み取りポート。
    ///
    /// 集約（TrackingActivity）を再構築せず、JOIN 結果を画面表示用の Read Model へ
    /// 直接デコードした結果を返す。
    ///
    pub eff ReadDb {
        def findByTrackingNumber(trackingNumber: String): Option[TrackingDetailView]
    }
}
