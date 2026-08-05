///
/// 追跡例外の記録と解決（US19。IT12）。
///
/// **手動更新（`UpdateTrackingStatus`）と別のサービスにする**。
/// 例外は理由と対応の記録を伴う業務であり、状態だけを付け替える操作とは
/// 規則が違う——IT11 で `EXCEPTION` を手動更新から選べないようにしたのは
/// このためである。規則が違うものは入口を分ける。
///
mod TrackingHandleException {
    use TrackingModel.{ExceptionType, TrackingError}
    use TrackingRepo.TrackingRepo

    pub enum ExceptionResult with Eq, ToString {
        case Raised
        case Resolved
        case NotFound
        case Rejected(TrackingError)
    }

    ///
    /// 例外を記録する（受入基準 1・2）。
    ///
    /// **`findForUpdate` で行を押さえる**。荷役の登録・手動更新と同じ行を書く。
    /// 押さえないと、荷役の反映と交錯して**退避した発生前の状態が
    /// 荷役の結果で上書きされる**——解決しても誤った状態へ戻る。
    ///
    /// `now` は**引数で受け取る**。エスカレーションの判定に要るが、
    /// 集約もサービスも現在時刻を取りに行かない（テストで固定できる）
    pub def raise(trackingNumber: String, exceptionType: ExceptionType,
                  location: String, occurredAt: String, description: String,
                  now: String): ExceptionResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => ExceptionResult.NotFound
            case Some(a) =>
                match TrackingModel.raiseException(a, exceptionType, location,
                                                   occurredAt, description,
                                                   TrackingModel.shouldEscalate(
                                                       occurredAt, now)) {
                    case Err(e)      => ExceptionResult.Rejected(e)
                    case Ok(updated) => {
                        TrackingRepo.saveHandling(updated);
                        ExceptionResult.Raised
                    }
                }
        }

    ///
    /// 例外を解決する（受入基準 5）。
    ///
    /// **発生前の状態へ戻す**。戻り先は集約が持っている（`statusBeforeException`）。
    ///
    pub def resolve(trackingNumber: String, resolvedAt: String,
                    resolutionNotes: Option[String],
                    revisedArrival: Option[String]):
            ExceptionResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => ExceptionResult.NotFound
            case Some(a) =>
                match TrackingModel.resolveException(a, resolvedAt,
                                                     resolutionNotes,
                                                     revisedArrival) {
                    case Err(e)      => ExceptionResult.Rejected(e)
                    case Ok(updated) => {
                        TrackingRepo.saveHandling(updated);
                        ExceptionResult.Resolved
                    }
                }
        }

    ///
    /// 対応内容だけを記録する（受入基準 4）。
    ///
    /// **解決とは分ける**。新しい到着予定が決まっても、遅延そのものが
    /// 解消したとは限らない——現場では別の操作である。
    /// 状態は `EXCEPTION` のまま据え置く。
    ///
    pub def recordResolution(trackingNumber: String,
                             resolutionNotes: Option[String],
                             revisedArrival: Option[String]):
            ExceptionResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => ExceptionResult.NotFound
            case Some(a) =>
                if (not TrackingModel.hasActiveException(a))
                    ExceptionResult.Rejected(TrackingError.NoActiveException)
                else {
                    TrackingRepo.saveHandling(
                        TrackingModel.withResolutionNotes(a, resolutionNotes,
                                                          revisedArrival));
                    ExceptionResult.Resolved
                }
        }
}
