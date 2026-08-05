///
/// 荷役の結果を追跡へ反映する（US15。IT10。IT11 で出来事も残すようにした）。
///
/// **Handling が呼ぶ**（`composition/acl/` のアダプタ経由）。
///
mod TrackingApplyHandling {
    use SharedTransportStatus.TransportStatus
    use TrackingModel.HandlingEvent
    use TrackingRepo.TrackingRepo

    pub enum ApplyResult with Eq, ToString {
        case Applied
        case NotFound
    }

    ///
    /// 荷役の出来事を記録し、輸送状態を進める。
    ///
    /// **状態は呼び出し元が決めて渡す**。荷役の種別と輸送状態の対応
    /// （積込 → 積み込み済）は荷役の業務知識であり、Tracking の関心ではない。
    ///
    /// **遷移の妥当性は今は見ない**。荷役は起きた事実の記録であり、
    /// 「受領していないのに積み込まれた」は**業務として実際に起こる**
    /// （受領の記録を忘れた等）。ここで拒むと現場が記録できなくなる——
    /// 記録を優先し、ずれは荷役の妥当性判定（`isValidFor`）が拾う。
    ///
    /// **出来事と状態を同じ呼び出しで書く**（IT11・H8）。IT10 は状態しか
    /// 書かず、荷主のタイムラインは空のままだった——バッジが変わるだけで
    /// 「いつ・どこで積まれたか」が出なかった。US15 の「なぜ」は
    /// 荷主がリアルタイムで確認できることであり、そこが欠けていた。
    ///
    /// **`findForUpdate` で行を押さえる**。荷役の同時登録では、押さえないと
    /// 輸送状態が後着順で決まり、積込の後に遅れて着いた受領が上書きする。
    ///
    /// **`status` が `None` なら出来事だけを残す**（IT11・ADR-0014 決定 2）。
    /// 通関は貨物の位置も輸送の段階も変えない。現在の状態を書き戻す形にすると、
    /// 書き戻しの競合を隠す。
    pub def apply(trackingNumber: String, status: Option[TransportStatus],
                  occurred: HandlingEvent): ApplyResult \ TrackingRepo =
        match TrackingRepo.findForUpdate(trackingNumber) {
            case None    => ApplyResult.NotFound
            case Some(a) => {
                TrackingRepo.saveHandling(TrackingModel.applyHandling(a, status, occurred));
                ApplyResult.Applied
            }
        }
}
