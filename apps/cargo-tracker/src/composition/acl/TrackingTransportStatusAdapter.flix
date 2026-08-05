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
    use HandlingModel.HandlingActivity
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
            def apply(activity, status, k) = k(applyInTracking(activity, status))
        }

    ///
    /// Handling の記録を Tracking の出来事へ翻訳する（IT11・H8）。
    ///
    /// **ここが唯一の翻訳点である**。`HandlingType` は Handling の型であり
    /// Tracking からは見えないため、永続化表現（`RECEIVE` 等）へ落として渡す。
    /// 荷役の妥当性（`HandlingValidity`）は**渡さない**——経路逸脱かどうかは
    /// 荷役の内部事情であり、荷主のタイムラインに出すものではない。
    ///
    def applyInTracking(activity: HandlingActivity,
                        status: Option[SharedTransportStatus.TransportStatus]):
            UpdateResult \ TrackingRepo =
        let occurred = TrackingModel.event(
            HandlingModel.typeToPersisted(HandlingModel.handlingTypeOf(activity)),
            HandlingModel.eventCompletionTimeOf(activity),
            HandlingModel.locationOf(activity),
            HandlingModel.voyageNumberOf(activity));
        match TrackingApplyHandling.apply(
                HandlingModel.trackingNumberOf(activity), status, occurred) {
            case TrackingApplyHandling.ApplyResult.Applied  => UpdateResult.Updated
            case TrackingApplyHandling.ApplyResult.NotFound => UpdateResult.TrackingNotFound
        }
}
