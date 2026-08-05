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
    /// **遷移の妥当性は今は見ない**。荷役は起きた事実の記録であり、
    /// 「受領していないのに積み込まれた」は**業務として実際に起こる**
    /// （受領の記録を忘れた等）。ここで拒むと現場が記録できなくなる——
    /// 記録を優先し、ずれは荷役の妥当性判定（`isValidFor`）が拾う。
    ///
    /// **それでも集約を通す**（`TrackingModel.applyHandling`）。規則が無いことと、
    /// 規則を書く場所が無いことは違う——US16 で `CLAIMED` を足すときに要る。
    ///
    /// **`findForUpdate` で行を押さえる**。荷役の同時登録では、押さえないと
    /// 輸送状態が後着順で決まり、積込の後に遅れて着いた受領が上書きする。
    ///
    pub def apply(trackingNumber: String, status: TransportStatus):
            ApplyResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => ApplyResult.NotFound
            case Some(a) => {
                TrackingRepo.saveTransportStatus(TrackingModel.applyHandling(a, status));
                ApplyResult.Applied
            }
        }
}
