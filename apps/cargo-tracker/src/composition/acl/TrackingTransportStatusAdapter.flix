///
/// 輸送状態の反映の翻訳（Handling → Tracking の ACL アダプタ。IT10・US15）。
///
/// **BC 間の翻訳の唯一の置き場所**である（`arch-lint` 規約 11・ADR-0012）。
///
/// 本アダプタは判断を持たない。Tracking のアプリケーションサービスを呼び、
/// **相手の集約を通す**。`tracking_activity` へ直接書く形は採らない——
/// 直接書くと `TrackingActivity` の不変条件を完全に迂回する。
///
mod TrackingTransportStatusAdapter {
    use HandlingTrackingTransportStatusUpdate.{TrackingTransportStatusUpdate, UpdateResult}
    use TrackingRepo.TrackingRepo

    /// 本アダプタが要求する効果（配線ファイルに Tracking の効果名を書かせない）
    pub type alias Requires = TrackingRepo

    pub def withTrackingTransportStatusUpdate(
            f: Unit -> a \ ef + TrackingTransportStatusUpdate):
            a \ (ef - TrackingTransportStatusUpdate) + Requires =
        run {
            f()
        } with handler TrackingTransportStatusUpdate {
            def apply(trackingNumber, status, k) = k(applyInTracking(trackingNumber, status))
        }

    def applyInTracking(trackingNumber: String,
                        status: SharedTransportStatus.TransportStatus):
            UpdateResult \ TrackingRepo =
        match TrackingApplyHandling.apply(trackingNumber, status) {
            case TrackingApplyHandling.ApplyResult.Applied  => UpdateResult.Updated
            case TrackingApplyHandling.ApplyResult.NotFound => UpdateResult.TrackingNotFound
        }
}
