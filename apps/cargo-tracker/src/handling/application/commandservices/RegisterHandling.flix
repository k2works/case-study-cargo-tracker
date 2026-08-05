///
/// 荷役作業の登録（US15。IT10）。
///
/// # 手順
///
/// 1. 追跡番号で貨物の写しを引く（ACL・読み）
/// 2. 集約が入力を検証し、写しと突き合わせて妥当性を判定する
/// 3. 記録を保存する
/// 4. 輸送状態を反映する（ACL・書き）
/// 5. MISROUTED なら予約へ報告する（ACL・書き）
///
/// **記録は必ず残す**。場所が予定と違っても記録を拒まない——
/// 予定と違うことは入力の誤りではなく**業務上起こりうる事実**であり、
/// 拒むと現場が記録できなくなる。ずれは妥当性として残し、警告で伝える。
///
/// **貨物が見つからなければ何も書かない**。追跡番号の打ち間違いは日常的に起きる。
///
mod HandlingRegisterHandling {
    use HandlingBookingMisroutingReport.BookingMisroutingReport
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingModel.{HandlingActivity, HandlingError, HandlingType, HandlingValidity}
    use HandlingRepo.HandlingRepo
    use HandlingTrackingTransportStatusUpdate.TrackingTransportStatusUpdate
    use SharedClock.Clock

    pub enum RegisterResult with Eq, ToString {
        /// 記録した（妥当性を添えて返す。画面が注意文を出す）
        case Registered(HandlingValidity)
        /// 追跡番号に対応する貨物が無い
        case CargoNotFound
        /// 入力が成立しない
        case Rejected(HandlingError)
    }

    pub def register(trackingNumber: String, handlingType: HandlingType,
                     location: String, completionTime: String,
                     voyageNumber: Option[String]):
            RegisterResult \ HandlingRepo + CargoSnapshotSource + Clock
                           + TrackingTransportStatusUpdate + BookingMisroutingReport =
        match HandlingModel.register(trackingNumber, handlingType, location,
                                     completionTime, voyageNumber, Clock.today()) {
            case Err(e) => RegisterResult.Rejected(e)
            case Ok(activity) =>
                match CargoSnapshotSource.findByTrackingNumber(HandlingModel.trackingNumberOf(activity)) {
                    case None           => RegisterResult.CargoNotFound
                    case Some(snapshot) => {
                        let validity = HandlingModel.isValidFor(activity, snapshot);
                        HandlingRepo.save(HandlingModel.withValidity(activity, validity));
                        // **輸送状態は妥当性によらず進める**。貨物は実際に
                        // 積み込まれており、荷主が見る画面はその事実を出す必要がある
                        discard TrackingTransportStatusUpdate.apply(
                            HandlingModel.trackingNumberOf(activity),
                            HandlingModel.resultingStatusOf(handlingType));
                        if (validity == HandlingValidity.Misrouted)
                            // **報告の失敗で荷役の記録を失わせない**。記録は済んでおり、
                            // ここで例外にすると現場は「登録できなかった」と受け取る
                            discard BookingMisroutingReport.report(
                                HandlingModel.bookingIdOf(snapshot))
                        else ();
                        RegisterResult.Registered(validity)
                    }
                }
        }
}
