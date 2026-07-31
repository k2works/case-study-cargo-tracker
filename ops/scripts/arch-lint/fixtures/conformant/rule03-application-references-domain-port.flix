// 適法: application 層が domain の効果宣言（ポート）を参照する
// パス: src/tracking/application/queryservices/Ok.flix 相当
mod TrackingOkApplication {
    use TrackingReadDb.ReadDb
    use TrackingView.TrackingDetailView

    pub def ok(n: String): Option[TrackingDetailView] \ ReadDb =
        ReadDb.findByTrackingNumber(n)
}
