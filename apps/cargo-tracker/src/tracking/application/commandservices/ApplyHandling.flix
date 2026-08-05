///
/// 荷役の結果を輸送状態へ反映する（US15。IT10）。
///
/// **Handling が呼ぶ**（`composition/acl/` のアダプタ経由）。
///
mod TrackingApplyHandling {
    use SharedTransportStatus.TransportStatus
    use TrackingRepo.TrackingRepo

    pub enum ApplyResult with Eq, ToString {
        case Applied
        case NotFound
    }

    ///
    /// 輸送状態を書き換える。
    ///
    /// **状態は呼び出し元が決めて渡す**。荷役の種別と輸送状態の対応
    /// （積込 → 積み込み済）は荷役の業務知識であり、Tracking の関心ではない。
    ///
    /// **遷移の妥当性は見ない**。荷役は起きた事実の記録であり、
    /// 「受領していないのに積み込まれた」は**業務として実際に起こる**
    /// （受領の記録を忘れた等）。ここで拒むと現場が記録できなくなる——
    /// 記録を優先し、ずれは荷役の妥当性判定（`isValidFor`）が拾う。
    ///
    pub def apply(trackingNumber: String, status: TransportStatus):
            ApplyResult \ TrackingRepo =
        match TrackingRepo.findByTrackingNumber(trackingNumber) {
            case None    => ApplyResult.NotFound
            case Some(_) => {
                TrackingRepo.updateTransportStatus(trackingNumber, status);
                ApplyResult.Applied
            }
        }
}
